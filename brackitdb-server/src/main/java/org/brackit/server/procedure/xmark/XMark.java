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
package org.brackit.server.procedure.xmark;

import java.text.SimpleDateFormat;
import java.util.Random;

import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.procedure.Procedure;
import org.brackit.server.procedure.ProcedureException;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Sequence;

/**
 * @author Sebastian Baechle
 */
public class XMark implements Procedure {
	private static final String[] PARAMETER = new String[] {
			"[PLACEBID | REGISTER | READSELLERINFO] - name of operation",
			"DOCUMENT - name of the document",
			"OPTIMIZED - use update optimization" };

	private final static String INFO = "Performs a randomized 'business operation' on an xmark document";

	protected static final SimpleDateFormat dateFormat = new SimpleDateFormat(
			"MM/dd/yyyy");
	protected static final SimpleDateFormat timeFormat = new SimpleDateFormat(
			"HH:mm:ss");

	protected static Random elementRandom = new Random();
	protected static Random amountRandom = new Random();

	public Sequence execute(TXQueryContext ctx, String... params)
			throws QueryException {
		DBCollection<?> coll;
		int procedureCode;
		String procedure;
		boolean optimized;

		try {
			ProcedureUtil.checkParameterCount(params, 2, 3, PARAMETER);

			coll = (DBCollection<?>) ctx.getStore().lookup(params[1]);
			procedure = params[0];
			optimized = (params.length == 3) ? Boolean.parseBoolean(params[2])
					: false;
			procedure = procedure.toUpperCase();
			// System.out.println(String.format("[%s] Start %s %s",
			// Thread.currentThread().getName(),
			// context.getTransaction().toShortString(), procedure));
			if (procedure.equals("PLACEBID"))
				procedureCode = 0;
			else if (procedure.equals("REGISTER"))
				procedureCode = 1;
			else if (procedure.equals("READSELLERINFO"))
				procedureCode = 2;
			else if (procedure.equals("CHANGEUSERINFO"))
				procedureCode = 3;
			else if (procedure.equals("CHECKMAILS"))
				procedureCode = 4;
			else if (procedure.equals("READITEM"))
				procedureCode = 5;
			else if (procedure.equals("DELETEMAIL"))
				procedureCode = 6;
			else if (procedure.equals("ADDMAIL"))
				procedureCode = 7;
			else if (procedure.equals("ADDITEM"))
				procedureCode = 8;
			else if (procedure.equals("UPDATEITEMDESCRIPTION"))
				procedureCode = 9;
			else if (procedure.equals("CHANGESELLER"))
				procedureCode = 10;
			else if (procedure.equals("LOOKUPSELLER"))
				procedureCode = 11;
			else if (procedure.equals("UPDATEITEM"))
				procedureCode = 12;
			else
				throw new ProcedureException("Unknown operation: %s", procedure);
		} catch (NumberFormatException e) {
			throw new ProcedureException("Error reading parameters: "
					+ e.getMessage());
		}
		try {
			Sequence result = null;
			XMarkUtil util = new XMarkUtil();
			// System.out.println(String.format("[%s]: running op %s for %s",
			// Thread.currentThread().getName(), procedureCode,
			// context.getTransaction().toString()));
			try {
				switch (procedureCode) {
				case 0:
					result = util.placeBid(ctx, coll);
					break;
				case 1:
					result = util.register(ctx, coll, optimized);
					break;
				case 2:
					result = util.readSellerInfo(ctx, coll);
					break;
				case 3:
					result = util.changeUserData(ctx, coll);
					break;
				case 4:
					result = util.checkMails(ctx, coll);
					break;
				case 5:
					result = util.readItem(ctx, coll);
					break;
				case 6:
					result = util.deleteMail(ctx, coll);
					break;
				case 7:
					result = util.addMail(ctx, coll);
					break;
				case 8:
					result = util.addItem(ctx, coll, optimized);
					break;
				case 9:
					result = util.updateItemDescription(ctx, coll);
					break;
				case 10:
					result = util.changeSeller(ctx, coll, optimized);
					break;
				case 11:
					result = util.lookupSeller(ctx, coll);
					break;
				case 12:
					result = util.updateItem(ctx, coll);
					break;
				default:
					throw new ProcedureException("Unknown operation code: %s",
							procedureCode);
				}
			} catch (DocumentException e) {
				result = new Str(e.getMessage());
			} catch (Throwable e) {
				e.printStackTrace();
				result = new Str(e.getMessage());
			} finally {
				// Collection<LockServiceStatistics> statisticsList =
				// ctx.getTX().getLockCB().getStatistics();
				//
				// for (LockServiceStatistics stat : statisticsList)
				// {
				// result.addInfoObject(stat);
				// //System.out.println(stat.getName() + "  -> " +
				// stat.getRequestCount()) ;
				// }
			}
			// System.out.println(String.format("[%s]: finishing op %s for %s",
			// Thread.currentThread().getName(), procedureCode,
			// context.getTransaction().toString()));

			// System.out.println(String.format("[%s] End %s %s",
			// Thread.currentThread().getName(),
			// context.getTransaction().toShortString(), procedure));

			return result;
		} finally {
			// System.out.println(String.format("[%s]: failed op %s for %s",
			// Thread.currentThread().getName(), procedureCode,
			// context.getTransaction().toString()));
		}
	}

	public final String getName() {
		return getClass().getSimpleName();
	}

	public String[] getParameter() {
		return PARAMETER;
	}

	public String getInfo() {
		return INFO;
	}
}
