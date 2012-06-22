/*
 * [New BSD License]
 * Copyright (c) 2011-2012, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Brackit Project Team nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.node.bracket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brackit.server.BrackitDB;
import org.brackit.server.ServerException;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.store.index.bracket.BracketIndex;
import org.brackit.server.store.index.bracket.StreamIterator;
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.server.store.index.bracket.filter.PSNodeFilter;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * 
 * @author Martin Hiller
 * 
 */
public class OutputPrefetch {

	private static abstract class Predicate {
		public final String attribute;

		public Predicate(String attribute) {
			this.attribute = attribute;
		}

		public abstract boolean eval(String value);
	}

	private static Tx tx;
	private static BracketCollection coll;
	private static BracketIndex index;
	private static BracketNode doc;

	// BEGIN: specify query
	private static String mainPath = "/site/people/person";
	private static Predicate pred = new Predicate("id") {
		@Override
		public boolean eval(String value) {
			return value.compareTo("person1587") <= 0;
		}
	};
	private static String[] outputPaths = new String[] { "/profile/gender",
			"/profile/age", "/profile/education" };

	private static String returnFormat;
	static {
		StringBuilder sb = new StringBuilder();
		sb.append("<personne>\n");
		sb.append("\t<statistiques>\n");
		sb.append("\t\t<sexe>%s</sexe>\n");
		sb.append("\t\t<age>%s</age>\n");
		sb.append("\t\t<education>%s</education>\n");
		sb.append("\t</statistiques>\n");
		sb.append("</personne>\n");
		returnFormat = sb.toString();
	}
	// END: specify query

	/**
	 * @param args
	 * @throws ServerException
	 * @throws DocumentException
	 */
	public static void main(String[] args) throws ServerException,
			DocumentException {

		// start server
		BrackitDB brackitDB = new BrackitDB(false);

		// get TX
		tx = brackitDB.getTaMgr().begin(IsolationLevel.NONE, null, true);

		// get collection / document
		coll = (BracketCollection) brackitDB.getMetadataMgr().lookup(tx,
				"/xmark.xml");
		doc = coll.getDocument();

		// get index
		index = coll.store.index;

		executeQueryWithPrefetch();

		// commit TX
		tx.commit();
		// shutdown DB
		brackitDB.shutdown();
	}

	private static void executeQueryWithPrefetch() throws DocumentException {

		// start time measuring

		// lookup PCRs for following MultiChildStream access
		Path<QNm> path = Path.parse(mainPath);
		int[] pcrs = coll.pathSynopsis.matchChildPath(path);
		BracketFilter[] mainPathFilters = new BracketFilter[pcrs.length];
		PSNode psNode = null;
		for (int i = 0; i < pcrs.length; i++) {
			PSNode current = coll.pathSynopsis.get(pcrs[i]);
			mainPathFilters[i] = new PSNodeFilter(coll.pathSynopsis, current);
			if (i == pcrs.length - 1) {
				psNode = current;
			}
		}

		// lookup PCR for evaluating where clause
		PSNode attributePSNode = coll.pathSynopsis.getChildIfExists(
				psNode.getPCR(), new QNm(pred.attribute), Kind.ATTRIBUTE.ID,
				null);
		if (attributePSNode == null) {
			throw new RuntimeException();
		}

		// lookup PCRs for the output paths
		BracketFilter[][] outputPathFilters = new BracketFilter[outputPaths.length][];
		for (int i = 0; i < outputPaths.length; i++) {
			Path<QNm> outputPath = Path.parse(outputPaths[i]);
			pcrs = coll.pathSynopsis.matchChildPath(path.copy().append(
					outputPath));
			pcrs = Arrays.copyOfRange(pcrs, path.getLength(), pcrs.length);
			outputPathFilters[i] = new BracketFilter[pcrs.length + 1];

			for (int j = 0; j < pcrs.length; j++) {
				outputPathFilters[i][j] = new PSNodeFilter(coll.pathSynopsis,
						pcrs[j]);
			}

			// to obtain the text node, use the last PSNodeFilter twice
			outputPathFilters[i][pcrs.length] = outputPathFilters[i][pcrs.length - 1];
		}

		// open MultiChildStream
		StreamIterator iter = index.openMultiChildStream(doc.locator,
				doc.getDeweyID(), doc.hintPageInfo, mainPathFilters);
		while (iter.moveNext()) {
			// for each node: check where clause

			// check attribute predicate
			StreamIterator attributeStream = index.forkAttributeStream(iter,
					new PSNodeFilter(coll.pathSynopsis, attributePSNode));
			BracketNode attribute = attributeStream.next();
			attributeStream.close();
			boolean conditionFulfilled = pred.eval(attribute.getValue()
					.stringValue());

			if (conditionFulfilled) {
				// load data

				// load BracketNode itself
				BracketNode qualified = iter.loadCurrent();

				// DEBUG: print complete subtree of qualified node
				// StreamIterator s = index.forkSubtreeStream(iter, null, true,
				// false);
				// while (s.moveNext()) {
				// System.out.println(s.loadCurrent());
				// }
				// s.close();

				// use a 2D string array:
				// 1st dimension: different output paths
				// 2nd dimension: String value for each qualified text node
				qualified.outputPrefetch = new String[outputPathFilters.length][];

				// prefetch data for output
				List<String> collect = new ArrayList<String>();
				for (int i = 0; i < outputPathFilters.length; i++) {

					// open one MultiChildStream per output path (optimization
					// potential: extract common paths, or even use a
					// "brute force" subtree stream)
					StreamIterator outputIter = index.forkMultiChildStream(
							iter, outputPathFilters[i]);
					while (outputIter.moveNext()) {
						BracketNode textNode = outputIter.loadCurrent();
						collect.add(textNode.getValue().stringValue());
					}
					outputIter.close();

					qualified.outputPrefetch[i] = collect
							.toArray(new String[collect.size()]);
					collect.clear();
				}

				// System.out.println(qualified);
				// for (int i = 0; i < outputPaths.length; i++) {
				// System.out.println(String.format("Prefetch for path %s: %s",
				// outputPaths[i],
				// Arrays.toString(qualified.outputPrefetch[i])));
				// }

				// return statement
				String[] output = new String[outputPaths.length];
				for (int i = 0; i < output.length; i++) {
					output[i] = (qualified.outputPrefetch[i].length == 0) ? ""
							: ((qualified.outputPrefetch[i].length == 1) ? qualified.outputPrefetch[i][0]
									: Arrays.toString(qualified.outputPrefetch[i]));
				}
				System.out.println(String.format(returnFormat, (Object[]) output));
			}
		}
		iter.close();

	}
}
