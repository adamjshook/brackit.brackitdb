/*
 * [New BSD License]
 * Copyright (c) 2011, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
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
package org.brackit.server.store.index.aries;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.Field;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.store.index.aries.page.PageContextFactory;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.thread.Latch;

/**
 * Helper class for accessing the fields and entries in an index page.
 * 
 * @author Sebastian Baechle
 * 
 */
public final class IndexPageHelper extends PageContextFactory {
	public IndexPageHelper(BufferMgr bufferMgr) {
		super(bufferMgr);
	}

	public void checkIndexConsistency(Tx transaction, Buffer buffer,
			PageID rootPageID) throws IndexOperationException {
		checkIndexConsistencyInternal(transaction, buffer, rootPageID, null);
	}

	private byte[] checkIndexConsistencyInternal(Tx transaction, Buffer buffer,
			PageID pageID, byte[] separatorKey) throws IndexOperationException {
		Handle handle;
		try {
			handle = buffer.fixPage(transaction, pageID);
		} catch (BufferException e) {
			throw new IndexOperationException(e, "Could not fix page %s.",
					pageID);
		}

		handle.latchS();
		PageContext context = create(transaction, buffer, handle);
		int pageType = context.getPageType();
		Field keyType = context.getKeyType();
		Field field = context.getValueType();

		byte[] currentKey = null;
		byte[] previousKey = null;

		if (pageType == PageType.INDEX_TREE) {
			try {
				if (context.getKey() == null) {
					throw new AssertionError(String.format(
							"Tree page %s is empty", pageID));
				}

				PageID currentChild = context.getBeforePageID();
				byte[] childHighKey = null;

				do {
					currentKey = context.getKey();

					try {
						if (separatorKey != null)
							if (!((separatorKey == null) || keyType.compare(
									currentKey, separatorKey) <= 0)) {
								throw new AssertionError(
										String
												.format(
														"child separator key '%s' (page %s) > next separator key '%s' in an ancestor",
														keyType
																.toString(currentKey),
														handle,
														keyType
																.toString(separatorKey)));
							}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						System.out.println(context);
						e.printStackTrace();
						System.exit(-1);
					}

					childHighKey = checkIndexConsistencyInternal(transaction,
							buffer, currentChild, currentKey);

					if (childHighKey != null)
						if (!((childHighKey == null) || keyType.compare(
								childHighKey, currentKey) < 1)) {
							throw new AssertionError(
									String
											.format(
													"child highKey '%s' (page %s) < next separator key '%s (page %s)'",
													keyType
															.toString(childHighKey),
													currentChild,
													keyType
															.toString(currentKey),
													handle.getPageID()));
						}

					currentChild = context.getAfterPageID();

					if (previousKey != null) {
						int comparison = keyType.compare(previousKey,
								currentKey);

						if (context.isUnique()) {
							if (!(comparison < 0)) {
								throw new AssertionError(
										String
												.format(
														"previous separator key '%s' < currentSeparatorKey '%s'",
														keyType
																.toString(previousKey),
														keyType
																.toString(currentKey)));
							}
						} else {
							if (!(comparison <= 0)) {
								throw new AssertionError(
										String
												.format(
														"previous separator key '%s' <= currentSeparatorKey '%s'",
														keyType
																.toString(previousKey),
														keyType
																.toString(currentKey)));
							}
						}
					}

					previousKey = currentKey;
				} while (context.hasNext());

				checkIndexConsistencyInternal(transaction, buffer,
						currentChild, separatorKey);

			} catch (Error e) {
				System.out.println(context.dump("Invalid page"));
				throw e;
			}
		} else if (pageType == PageType.INDEX_LEAF) {
			byte[] previousValue = null;
			byte[] currentValue = null;

			do {
				currentKey = context.getKey();
				currentValue = context.getValue();

				if (previousKey != null) {
					int comparison = keyType.compare(previousKey, currentKey);
					try {
						if (!(comparison < 1)) {
							throw new AssertionError(String.format(
									"previousKey '%s' > currentKey '%s'",
									keyType.toString(previousKey), keyType
											.toString(currentKey)));
						}
					} catch (Error e) {
						System.err.println(context.dump("illegal page"));
						throw e;
					}

					if (comparison == 0) {
						try {
							if (!(field.compare(previousValue, currentValue) < 0)) {
								throw new AssertionError(
										String
												.format(
														"previousValue '%s' >= currentValue '%s'",
														field
																.toString(previousValue),
														field
																.toString(currentValue)));
							}
						} catch (Error e) {
							System.err.println(context.dump("illegal page"));
							throw e;
						}
					}
				}

				previousKey = currentKey;
				previousValue = currentValue;
			} while (context.hasNext());
		} else {
			handle.unlatch();
			try {
				buffer.unfixPage(handle);
			} catch (BufferException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			throw new AssertionError(String
					.format("Page %s has an unexpected page type: %s", pageID,
							pageType));
		}

		handle.unlatch();
		try {
			buffer.unfixPage(handle);
		} catch (BufferException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return currentKey;
	}

	public void verifyLeftHighKey(PageContext leftPageContext,
			PageContext parentPageContext, byte[] separatorKey, Field keyType,
			Field field) throws IndexOperationException {
		PageContext context = leftPageContext.createClone();
		context.moveFirst();
		while (context.hasNext())
			;
		int comparison = keyType.compare(context.getKey(), separatorKey);

		if (comparison > 0) {
			log(parentPageContext.createClone().dump("parent page"));
			log(leftPageContext.createClone().dump("left page"));
			throw new IndexOperationException(
					"High key %s of left page %s (handle %s) is greater than separator key %s in parent page %s (handle %s).",
					keyType.toString(context.getKey()), leftPageContext
							.getPageID(), leftPageContext, keyType
							.toString(separatorKey), parentPageContext
							.getPageID(), parentPageContext);
		}
	}

	public void verifyRightLowKey(PageContext rightPageContext,
			PageContext parentPageContext, byte[] separatorKey, Field keyType,
			Field field) throws IndexOperationException {
		PageContext context = rightPageContext.createClone();
		context.moveFirst();

		int comparison = keyType.compare(separatorKey, context.getKey());

		if (comparison > 0) {
			log(parentPageContext.createClone().dump("parent page"));
			log(rightPageContext.createClone().dump("right page"));
			throw new IndexOperationException(
					"Separator key %s in parent page %s (handle %s) is greater than low key %s in right page %s (handle %s).",
					keyType.toString(separatorKey), parentPageContext
							.getPageID(), parentPageContext, keyType
							.toString(context.getKey()), rightPageContext
							.getPageID(), rightPageContext);
		}
	}

	public void verifyLeftLowKey(PageContext leftPageContext,
			PageContext parentPageContext, byte[] separatorKey, Field keyType,
			Field field) throws IndexOperationException {
		PageContext parentContext = parentPageContext.createClone();
		parentContext.moveFirst();

		while ((keyType.compare(parentContext.getKey(), separatorKey) < 0)
				&& parentContext.hasNext())
			;

		if (parentContext.getPreviousKey() != null) {
			PageContext leftContext = leftPageContext.createClone();
			leftContext.moveFirst();

			int comparison = keyType.compare(parentContext.getPreviousKey(),
					leftContext.getKey());

			if (comparison > 0) {
				log(parentContext.getKey() != null ? keyType
						.toString(parentContext.getKey()) : null);
				log(parentContext.getPreviousKey() != null ? keyType
						.toString(parentContext.getPreviousKey()) : null);
				log(parentPageContext.createClone().dump("parent page"));
				log(leftPageContext.createClone().dump("left page"));
				throw new IndexOperationException(
						"Key %s (< separator Key %s) in parent page %s (handle %s) is greater than low key %s in left page %s (handle %s).",
						keyType.toString(parentContext.getPreviousKey()),
						keyType.toString(separatorKey), parentPageContext
								.getPageID(), parentPageContext, keyType
								.toString(leftContext.getKey()),
						leftPageContext.getPageID(), leftPageContext);
			}
		}
	}

	public void verifyPreviousHighKey(PageContext pageHandle,
			PageContext previousPageContext, Field keyType, Field field)
			throws IndexOperationException {
		PageContext previousContext = previousPageContext.createClone();
		previousContext.moveFirst();

		previousContext.moveAfterLast();

		if (previousContext.getPreviousKey() != null) {
			PageContext leftContext = pageHandle.createClone();
			leftContext.moveFirst();

			int comparison = keyType.compare(previousContext.getPreviousKey(),
					leftContext.getKey());

			if (comparison > 0) {
				log(previousPageContext.createClone().dump("previous page"));
				log(pageHandle.createClone().dump("page"));
				throw new IndexOperationException(
						"High Key %s in parent page %s (handle %s) is greater than low key %s in page %s (handle %s).",
						keyType.toString(previousContext.getPreviousKey()),
						previousPageContext.getPageID(), previousPageContext,
						keyType.toString(leftContext.getKey()), pageHandle
								.getPageID(), pageHandle);
			}
		}
	}

	public void verifyRightHighKey(PageContext rightPageContext,
			PageContext parentPageContext, byte[] separatorKey, Field keyType,
			Field field) throws IndexOperationException {
		PageContext parentContext = parentPageContext.createClone();
		parentContext.moveFirst();

		while ((keyType.compare(parentContext.getKey(), separatorKey) < 0)
				&& parentContext.hasNext())
			;

		if (parentContext.hasNext()) {
			PageContext rightContext = rightPageContext.createClone();
			rightContext.moveFirst();

			int comparison = keyType.compare(rightContext.getKey(),
					parentContext.getKey());

			if (comparison > 0) {
				log(parentPageContext.createClone().dump("parent page"));
				log(rightPageContext.createClone().dump("right page"));
				throw new IndexOperationException(
						"Key %s (> separator Key %s) in parent page %s (handle %s) is smaller than high key %s in right page %s (handle %s).",
						keyType.toString(parentContext.getKey()), keyType
								.toString(separatorKey), parentPageContext
								.getPageID(), parentPageContext, keyType
								.toString(rightContext.getKey()),
						rightPageContext.getPageID(), rightPageContext);
			}
		}
	}

	public void verifyPage(PageContext page) throws IndexOperationException {
		try {
			PageContext context = page.createClone();
			PageContext context2 = page.createClone();

			PageID pageNumber = page.getPageID();
			int pageType = context.getPageType();
			long LSN = page.getLSN();
			Field keyType = context.getKeyType();
			Field valueType = context.getValueType();

			byte[] value = null;

			if (!(context.moveFirst())) {
				throw new IndexOperationException("Index page is empty");
			}

			context.moveFirst();

			byte[] currentKey = null;
			byte[] currentValue = null;
			byte[] previousKey = null;
			byte[] previousValue = null;

			byte[] currentKey2 = null;
			byte[] currentValue2 = null;

			do {
				currentKey = context.getKey();
				currentValue = context.getValue();
				currentKey2 = context2.getKey();
				currentValue2 = context2.getValue();

				if (keyType.compare(currentKey, currentKey2) != 0) {
					throw new IndexOperationException(
							"verifyPage %s failed, key mismatch: cache key=%s, non-cache key=%s",
							pageNumber, keyType.toString(currentKey), keyType
									.toString(currentKey2));
				}
				if (valueType.compare(currentValue, currentValue2) != 0) {
					throw new IndexOperationException(
							"verifyPage %s failed, value mismatch: cache value=%s, non-cache value=%s",
							pageNumber, valueType.toString(currentValue),
							valueType.toString(currentValue2));
				}

				if (previousKey != null) {
					int comparison = keyType.compare(previousKey, currentKey);

					if (pageType == PageType.INDEX_TREE) {
						if (comparison >= 0)
							throw new IndexOperationException(
									"previousSeparatorKey '%s' >= currentSeparatorKey '%s'",
									keyType.toString(previousKey), keyType
											.toString(currentKey));
					} else {
						if (comparison > 0)
							throw new IndexOperationException(
									"previousKey '%s' > currentKey '%s'",
									keyType.toString(previousKey), keyType
											.toString(currentKey));
						else if (comparison == 0) {
							if (context.isUnique())
								throw new IndexOperationException(
										"previousKey '%s' == currentKey '%s' in unique index",
										keyType.toString(previousKey), keyType
												.toString(currentKey));
							else if (valueType.compare(previousValue,
									currentValue) >= 0)
								throw new IndexOperationException(
										"previousKey '%s' == currentKey '%s' and previousValue '%s' >= currentValue '%s'",
										keyType.toString(previousKey), keyType
												.toString(currentKey),
										valueType.toString(previousValue),
										valueType.toString(currentValue));
						}

					}
				}

				previousKey = currentKey;
				previousValue = currentValue;
			} while ((context.hasNext() && context2.hasNext()));

		} catch (IndexOperationException e) {
			System.out.println(page.createClone().dump("Invalid page"));
			throw e;
		}
	}

	private void log(String message) {
		System.out.println(String.format("[%s] %s", Thread.currentThread()
				.getName(), message));
	}

	public void verifySplit(PageContext left, PageContext right,
			PageContext parent, byte[] separatorKey, Field keyType,
			Field valueType) throws IndexOperationException {
		checkState(left, Latch.MODE_X, true);
		checkState(right, Latch.MODE_X, true);

		if (parent != null) {
			checkState(parent, Latch.MODE_X, true);
		}

		verifyPage(left);
		verifyPage(right);

		if (parent != null) {
			verifyPage(parent);
			verifyLeftLowKey(left, parent, separatorKey, keyType, valueType);
			try {
				verifyLeftHighKey(left, parent, separatorKey, keyType,
						valueType);
			} catch (IndexOperationException e) {
				log(right.createClone().dump("right page"));
				throw e;
			}
			verifyRightLowKey(right, parent, separatorKey, keyType, valueType);
			verifyRightHighKey(right, parent, separatorKey, keyType, valueType);
		}
	}

	public void verifyRootSplit(PageContext root, PageContext left,
			PageContext right, Field keyType, Field valueType,
			byte[] separatorKey) throws IndexOperationException {
		verifyPage(left);
		verifyPage(root);
		verifyLeftLowKey(left, root, separatorKey, keyType, valueType);
		verifyLeftHighKey(left, root, separatorKey, keyType, valueType);
		verifyPage(right);
		verifyRightLowKey(right, root, separatorKey, keyType, valueType);
		verifyRightHighKey(right, root, separatorKey, keyType, valueType);
	}

	public void checkState(PageContext ctx, int latchMode, boolean safe)
			throws IndexOperationException {
		boolean latchOK = false;

		switch (latchMode) {
		case Latch.MODE_S:
			latchOK = ctx.isLatchedS();
		case Latch.MODE_U:
			latchOK = ctx.isLatchedU();
		case Latch.MODE_X:
			latchOK = ctx.isLatchedX();
		default:
			latchOK = true;
		}

		if (!latchOK)
			throw new IllegalStateException(String.format(
					"Page %s is not in correct latch mode %s: %s", ctx
							.getPageID(), latchMode, ctx.getMode()));

		if (safe != ctx.isSafe())
			throw new IllegalStateException(String.format(
					"Page %s is not in correct safe mode %s: %s", ctx
							.getPageID(), safe, ctx.isSafe()));
	}
}
