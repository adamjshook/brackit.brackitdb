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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.procedure.xmark;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.tx.locking.services.EdgeLockService.Edge;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.Dbl;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.node.stream.StreamUtil;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Stream;
import org.xml.sax.InputSource;

/**
 * @author Sebastian Baechle
 * 
 */
public class XMarkUtil {
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat(
			"MM/dd/yyyy");
	private static final SimpleDateFormat timeFormat = new SimpleDateFormat(
			"HH:mm:ss");

	private final static String BID_TEMPLATE = "<bidder>"
			+ "<date>$DATE</date>" + "<time>$TIME</time>"
			+ "<personref person=\"$PERSON\"/>"
			+ "<increase>$INCREASE</increase>" + "</bidder>";

	public final static String PERSON_TEMPLATE = "<person id=\"$PERSON\">"
			+ "<name>Lihong Oppitz</name>"
			+ "<emailaddress>mailto:Oppitz@sfu.ca</emailaddress>"
			+ "<homepage>http://www.sfu.ca/~Oppitz</homepage>" + "</person>";

	public final static String MAIL_TEMPLATE = "<mail>"
			+ "<from>Dominic Takano mailto:Takano@yahoo.com</from>"
			+ "<to>Mechthild Renear mailto:Renear@acm.org</to>"
			+ "<date>10/12/1999</date>"
			+ "<text>asses scruple learned crowns preventions half "
			+ "whisper logotype weapons doors factious already pestilent sacks dram atwain "
			+ "girdles deserts flood park lest graves discomfort sinful conceiv therewithal motion stained preventions greatly suit observe si"
			+ "news enforcement armed gold gazing set almost catesby turned servilius cook doublet preventions shrunk"
			+ "</text>" + "</mail>";

	public final static String ITEM_TEMPLATE = "<item id=\"$ITEM\">"
			+ "<location>United States</location>"
			+ "<quantity>1</quantity>"
			+ "<name>duteous nine eighteen </name>"
			+ "<payment>Creditcard</payment>"
			+ "<description>"
			+ "<parlist>"
			+ "<listitem>"
			+ "<text>"
			+ "page rous lady idle authority capt professes stabs monster petition heave humbly removes rescue runs shady peace most piteous worser oak assembly holes patience but malice whoreson mirrors master tenants smocks"
			+ "</text>"
			+ "</listitem>"
			+ "<listitem>"
			+ "<text>shepherd noble supposed dotage humble servilius bitch theirs venus dismal wounds gum merely raise red breaks earth god folds closet captain dying reek"
			+ "</text>"
			+ "</listitem>"
			+ "</parlist>"
			+ "</description>"
			+ "<shipping>Will ship internationally, See description for charges</shipping>"
			+ "<incategory category=\"category0\"/>" + "<mailbox>"
			+ "</mailbox>" + "</item>";

	public final static String DESCRIPTIONTEXT_TEMPLATE = "<text>"
			+ "page rous lady idle authority capt professes stabs monster petition heave humbly removes rescue runs shady peace most piteous worser oak assembly holes patience but malice whoreson mirrors master tenants smocks"
			+ "</text>";

	private static SearchMode refNodeSearchMode;

	public XMarkUtil() {
		refNodeSearchMode = SearchMode.RANDOM_THREAD;
	}

	/**
	 * Constructor for setting the seed of the random generator (and setting it
	 * only at initialization)
	 * 
	 * @param nodeMgr
	 * @param indexController
	 * @param vocabularyControllerthis
	 * @param seed
	 */
	public XMarkUtil(int seed) {
		if (refNodeSearchMode == null) {
			refNodeSearchMode = SearchMode.RANDOM_SYSTEM;
			refNodeSearchMode.setSeed(seed);
		}
	}

	public Sequence register(TXQueryContext ctx, DBCollection<?> locator,
			boolean optimized) throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		String personID = null;
		TXNode<?> people = selectRefNode(ctx, locator, "people");

		if (optimized) {
			((TXNode) people).getNls().lockEdgeUpdate(ctx.getTX(),
					people.getDeweyID(), Edge.LAST_CHILD);
		}
		TXNode<?> prevPerson = people.getLastChild();

		if (prevPerson == null) {
			personID = "person0";
			InputSource newPerson = createPerson(personID);
			people.append(new DocumentParser(newPerson));
		} else {
			TXNode<?> attribute = prevPerson.getAttribute(new QNm("id"));
			String prevPersonID = attribute.getValue().stringValue();
			int prevPersonIDNumber = Integer
					.parseInt(prevPersonID.substring(6));
			personID = "person" + (prevPersonIDNumber + 1);
			InputSource newPerson = createPerson(personID);
			prevPerson.insertAfter(new DocumentParser(newPerson));
		}

		// System.out.println("personID = " + personID);
		return new Str("");
	}

	private InputSource createPerson(String personID) {
		String person = PERSON_TEMPLATE;
		person = person.replace("$PERSON", personID);
		InputSource newPerson = new InputSource(new StringReader(person));
		return newPerson;
	}

	private InputSource createMail() {
		String mail = MAIL_TEMPLATE;
		InputSource newMail = new InputSource(new StringReader(mail));
		return newMail;
	}

	private InputSource createItem(String itemID) {
		String item = ITEM_TEMPLATE;
		item = item.replace("$ITEM", itemID);
		InputSource newItem = new InputSource(new StringReader(item));
		return newItem;
	}

	private InputSource createDescription() {
		String description = DESCRIPTIONTEXT_TEMPLATE;
		InputSource newDescription = new InputSource(new StringReader(
				description));
		return newDescription;
	}

	public Sequence readItem(TXQueryContext ctx, DBCollection<?> locator)
			throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose an item
		TXNode<?> item = selectRefNode(ctx, locator, "item");

		List<? extends TXNode<?>> itemFragment = StreamUtil.asList(item
				.getSubtree());

		return new Str("");
	}

	public Sequence addMail(TXQueryContext ctx, DBCollection<?> locator)
			throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose a mailbox
		TXNode<?> mailbox = selectRefNode(ctx, locator, "mailbox");

		// insert new mail
		InputSource newMail = createMail();
		mailbox.prepend(new DocumentParser(newMail));
		return new Str("");
	}

	public Sequence addItem(TXQueryContext ctx, DBCollection<?> locator,
			boolean optimized) throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose an item
		TXNode<?> item = selectRefNode(ctx, locator, "item");

		TXNode<?> region = item.getParent();

		TXNode<?> savedItem = item;

		if (optimized) {
			((TXNode) region).getNls().lockEdgeUpdate(ctx.getTX(),
					region.getDeweyID(), Edge.LAST_CHILD);
		}
		item = region.getLastChild();

		TXNode<?> attribute = item.getAttribute(new QNm("id"));
		int prevItemIDNumber = 
			Integer.parseInt(attribute.getValue().stringValue().substring(4));
		String itemID = "item" + (prevItemIDNumber + 1);

		// insert new mail
		InputSource newItem = createItem(itemID);
		region.insertAfter(new DocumentParser(newItem));

		return new Str("");
	}

	public Sequence deleteMail(TXQueryContext ctx, DBCollection<?> locator)
			throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose a mail
		TXNode<?> mail = selectRefNode(ctx, locator, "mail");
		mail.delete();

		return new Str("");
	}

	public Sequence checkMails(TXQueryContext ctx, DBCollection<?> locator)
			throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();
		TXNode<?> mailbox = null;

		// choose an item
		TXNode<?> item = selectRefNode(ctx, locator, "item");
		item.getFirstChild();

		// fetch the mail box
		QNm mb = new QNm("mailbox");
		int indexNo = -1;
		for (IndexDef indexDef : locator.get(Indexes.class).getIndexDefs()) {
			if (indexDef.isNameIndex()) {
				if ((indexDef.getIncluded().containsKey(mb))
						|| (!indexDef.getExcluded().contains(mb))) {
					indexNo = indexDef.getID();
					break;
				}
			}
		}
		if (indexNo == -1) {
			throw new DocumentException(
					"This method requires an element index containing =mailbox= elements.");
		}
		Stream<? extends TXNode<?>> mailBoxes = locator.getIndexController()
				.openNameIndex(indexNo, mb, SearchMode.GREATER_OR_EQUAL);

		mailbox = mailBoxes.next();
		mailBoxes.close();

		if (mailbox != null) {
			for (TXNode<?> mail = mailbox.getFirstChild(); mail != null; mail = mail
					.getNextSibling()) {
				List<? extends TXNode<?>> mailFragment = StreamUtil.asList(mail
						.getSubtree());
			}
		}

		return new Str("");
	}

	public Sequence changeSeller(TXQueryContext ctx, DBCollection<?> locator,
			boolean optimized) throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose an offered item
		TXNode<?> openAuction = selectRefNode(ctx, locator, "open_auction");
		TXNode<?> person = selectRefNode(ctx, locator, "person");

		TXNode<?> attribute = person.getAttribute(new QNm("id"));
		if (attribute != null) {
			Atomic id = attribute.getValue();

			// find the seller entry
			String sellerID = null;
			TXNode<?> prevSibling = null;
			TXNode<?> currentValue = null;
			for (TXNode<?> child = openAuction.getFirstChild(); child != null; child = child
					.getNextSibling()) {
				QNm name = child.getName();

				if ("seller".equals(name.stringValue())) {
					child.setAttribute(new QNm("person"), id);
					break;
				}
			}
		}

		return new Str("");
	}

	public Sequence readSellerInfo(TXQueryContext ctx, DBCollection<?> locator)
			throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose an offered item
		TXNode<?> openAuction = selectRefNode(ctx, locator, "open_auction");

		// find the seller entry
		Atomic sellerID = null;
		TXNode<?> prevSibling = null;
		TXNode<?> currentValue = null;
		for (TXNode<?> child = openAuction.getFirstChild(); child != null; child = child
				.getNextSibling()) {
			String name = child.getName().stringValue();

			if ("seller".equals(name)) {
				sellerID = child.getAttribute(new QNm("person")).getValue();
				break;
			}
		}

		// find the correpsonding person entry
		TXNode<?> people = selectRefNode(ctx, locator, "people");
		for (TXNode<?> child = people.getFirstChild(); sellerID != null
				&& child != null; child = child.getNextSibling()) {
			TXNode<?> attribute = child.getAttribute(new QNm("id"));
			if ((attribute != null) && (sellerID.equals(attribute.getValue()))) {
				TXNode<?> name = null;
				TXNode<?> email = null;
				TXNode<?> phone = null;
				String nameValue = null;
				String emailValue = null;
				String phoneValue = null;
				name = child.getFirstChild();
				if (name != null) {
					nameValue = getElementValue(ctx, name);
					email = name.getNextSibling();
					if (email != null) {
						emailValue = getElementValue(ctx, email);
						phone = email.getNextSibling();
						if (phone != null) {
							phoneValue = getElementValue(ctx, phone);
						}
					}
				}
				// System.out.println(String.format("Name='%s'   email='%s'    phone='%s'",
				// nameValue, emailValue, phoneValue));
				break;
			}
		}

		return new Str("");

	}

	public Sequence lookupSeller(TXQueryContext ctx, DBCollection<?> locator)
			throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose an offered item
		TXNode<?> openAuction = selectRefNode(ctx, locator, "open_auction");

		// find the seller entry
		Atomic sellerID = null;
		TXNode<?> prevSibling = null;
		TXNode<?> currentValue = null;
		for (TXNode<?> child = openAuction.getFirstChild(); child != null; child = child
				.getNextSibling()) {
			String name = child.getName().stringValue();

			if ("seller".equals(name)) {
				sellerID = child.getAttribute(new QNm("person")).getValue();
				break;
			}
		}

		return new Str("");
	}

	public Sequence updateItem(TXQueryContext ctx, DBCollection<?> locator)
			throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose an item
		TXNode<?> item = selectRefNode(ctx, locator, "item");
		TXNode<?> prevSibling = null;

		// find an description entry
		for (TXNode<?> childOfItem = item.getFirstChild(); childOfItem != null; childOfItem = childOfItem
				.getNextSibling()) {
			QNm name = childOfItem.getName();

			if ("description".equals(name)) {
				TXNode<?> childOfDesc = childOfItem.getFirstChild();

				if (childOfDesc != null) {

					if ("text".equals(childOfDesc.getName())) {
						System.out
								.println("Updateting item description -- text");
						changeElementValue(
								ctx,
								childOfDesc,
								"idle authority capt professes stabs monster petition heave humbly removes rescue runs shady peace most piteous worser oak");
					} else if ("parlist".equals(childOfDesc.getName())) {
						System.out
								.println("Updating item description -- parlist");
						childOfDesc.delete();
						childOfItem.prepend(new DocumentParser(
								createDescription()));
					} else {
						System.out
								.println("xmark doc does not conform to the DTD.");
					}
				} else {
					System.out.println("childOfDesc == null");
				}
			}
		}

		return new Str("");
	}

	public Sequence updateItemDescription(TXQueryContext ctx,
			DBCollection<?> locator) throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose an item
		TXNode<?> item = selectRefNode(ctx, locator, "item");
		TXNode<?> prevSibling = null;

		// find an description entry
		for (TXNode<?> child = item.getFirstChild(); child != null; child = child
				.getNextSibling()) {
			String name = child.getName().stringValue();

			prevSibling = child;

			if ("description".equals(name)) {
				TXNode<?> parlist = child.getFirstChild();

				if ((parlist != null) && (parlist.getKind() != Kind.TEXT)) {
					TXNode<?> listitem = parlist.getFirstChild();

					if ((listitem != null) && (listitem.getKind() != Kind.TEXT)) {
						TXNode<?> text = listitem.getFirstChild();

						if (text != null) {
							System.out.println("Updateting item");
							changeElementValue(
									ctx,
									text,
									"idle authority capt professes stabs monster petition heave humbly removes rescue runs shady peace most piteous worser oak");
						} else {
							InputSource description = createDescription();
							System.out.print("inserting description  ");
							listitem.append(new DocumentParser(description));
							System.out.println("OK ");
						}
					} else {
						System.out
								.println("listitem == null, text, or a placeholder");
					}
				} else {
					System.out
							.println("parlist == null, text, or a placeholder");
				}
				break;
			}
		}

		return new Str("");
	}

	public Sequence changeUserData(TXQueryContext ctx, DBCollection<?> locator)
			throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		// choose a user
		TXNode<?> person = selectRefNode(ctx, locator, "person");

		// find the seller entry
		String sellerID = null;
		TXNode<?> prevSibling = null;
		TXNode<?> currentValue = null;

		TXNode<?> name = null;
		TXNode<?> email = null;
		TXNode<?> phone = null;
		String nameValue = null;
		String emailValue = null;
		String phoneValue = null;
		name = person.getFirstChild();
		if (name != null) {
			nameValue = getElementValue(ctx, name);
			email = name.getNextSibling();
			if (email != null) {
				emailValue = getElementValue(ctx, email);
				if (!changeElementValue(ctx, email, "me@benchmark.com")) {
					phone = email.getNextSibling();

					if (phone != null) {
						phoneValue = getElementValue(ctx, phone);
						changeElementValue(ctx, phone, "555-Schuhe");
					}
				}
			}
		}

		return new Str("");
	}

	private boolean changeElementValue(TXQueryContext ctx, TXNode<?> element,
			String value) throws DocumentException {
		((TXNode) element).getNls().lockEdgeUpdate(ctx.getTX(),
				element.getDeweyID(), Edge.LAST_CHILD);
		TXNode<?> textNode = element.getLastChild();

		if ((textNode != null) && (textNode.getKind() == Kind.TEXT)) {
			textNode.setValue(new QNm(value));
			return true;
		} else {
			return false;
		}
	}

	private String getElementValue(TXQueryContext ctx, TXNode<?> element)
			throws DocumentException {
		String value = null;
		TXNode<?> textNode = element.getFirstChild();

		if ((textNode != null) && (textNode.getKind() == Kind.TEXT))
			value = textNode.getValue().stringValue();
		return value;
	}

	public Sequence placeBid(TXQueryContext ctx, DBCollection<?> locator)
			throws DocumentException {
		TXNode<?> docRoot = locator.getDocument().getFirstChild();

		TXNode<?> person = selectRefNode(ctx, locator, "person");
		// System.out.println("person : " + person);
		TXNode<?> attribute = person.getAttribute(new QNm("id"));
		// System.out.println(nodeMgr.getAttributes(ctx, person));
		// System.out.println("attribute : " + attribute);
		String personID = attribute.getValue().stringValue();

		// choose an offered item
		TXNode<?> openAuction = selectRefNode(ctx, locator, "open_auction");

		double price = 1;
		TXNode<?> prevSibling = null;
		TXNode<?> currentValue = null;
		for (TXNode<?> child = openAuction.getFirstChild(); child != null; child = child
				.getNextSibling()) {
			String name = child.getName().stringValue();

			prevSibling = child;

			if ("initial".equals(name)) {
				TXNode<?> initialValue = child.getFirstChild();
				price = Double.parseDouble(initialValue.getValue()
						.stringValue());
			} else if ("current".equals(name)) {
				currentValue = child.getFirstChild();
				price = Double.parseDouble(currentValue.getValue().
						stringValue()) + 12;
			} else if ("itemref".equals(name)) {
				break;
			}

			((TXNode) prevSibling).getNls().lockEdgeShared(ctx.getTX(),
					prevSibling.getDeweyID(), Edge.NEXT_SIBLING);
			((TXNode) child).getNls().lockEdgeUpdate(ctx.getTX(),
					child.getDeweyID(), Edge.NEXT_SIBLING);
		}

		InputSource newBid = createBid(personID, price);

		// insert new bid
		TXNode<?> bid = null;
		if (prevSibling != null)
			bid = prevSibling.insertAfter(new DocumentParser(newBid));
		else
			bid = openAuction.append(new DocumentParser(newBid));

		// update current highest bid
		if (currentValue != null) {
			currentValue.setValue(new Dbl(price));
		} else {
			TXNode<?> current = bid.insertAfter(Kind.ELEMENT, 
					new QNm("current"), null);
			currentValue = current.append(Kind.TEXT, null, new Dbl(price));
		}

		return new Str("");
	}

	private InputSource createBid(String personID, double price) {
		String bid = BID_TEMPLATE;
		Date now = new Date();
		bid = bid.replace("$DATE", dateFormat.format(now));
		bid = bid.replace("$TIME", timeFormat.format(now));
		bid = bid.replace("$PERSON", personID);
		bid = bid.replace("$INCREASE", Double.toString(price));
		InputSource newBid = new InputSource(new StringReader(bid));
		return newBid;
	}

	private TXNode<?> selectRefNode(TXQueryContext ctx,
			DBCollection<?> locator, String name) throws DocumentException {
		QNm qname = new QNm(name);
		int indexNo = -1;
		for (IndexDef indexDef : locator.get(Indexes.class).getIndexDefs()) {
			if (indexDef.isNameIndex()) {
				if ((indexDef.getIncluded().containsKey(qname))
						|| (!indexDef.getExcluded().contains(qname))) {
					indexNo = indexDef.getID();
					break;
				}
			}
		}
		if (indexNo == -1) {
			throw new DocumentException(
					"This method requires an element index containing =" + qname
							+ "= elements.");
		}

		Stream<? extends TXNode<?>> elementStream = locator
				.getIndexController().openNameIndex(indexNo, qname,
						refNodeSearchMode);
		TXNode<?> element = elementStream.next();
		return element;
	}
}
