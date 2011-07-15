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
package org.brackit.server.store.index.bracket;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.blob.BlobStore;
import org.brackit.server.store.blob.impl.SimpleBlobStore;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.bulkinsert.BulkInsertContext;
import org.brackit.server.store.index.bracket.bulkinsert.SeparatorEntry;
import org.brackit.server.store.index.bracket.page.BPContext;
import org.brackit.server.store.index.bracket.page.BracketContext;
import org.brackit.server.store.index.bracket.page.Branch;
import org.brackit.server.store.index.bracket.page.EmptyLeafException;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.index.bracket.page.PageContextFactory;
import org.brackit.server.store.index.bracket.stats.DefaultScanStats;
import org.brackit.server.store.index.bracket.stats.LastChildScanStats;
import org.brackit.server.store.index.bracket.stats.ScanStats;
import org.brackit.server.store.index.bracket.stats.PreviousSiblingScanStats;
import org.brackit.server.store.page.bracket.BracketNodeSequence;
import org.brackit.server.store.page.bracket.BracketPage;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;
import org.brackit.server.tx.PostCommitHook;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxStats;

/**
 * @author Martin Hiller
 * 
 */
public final class BracketTree extends PageContextFactory {

	private static final Logger log = Logger.getLogger(BracketTree.class);
	private static final KeyNotExistentException KEY_NOT_EXISTENT = new KeyNotExistentException();

	private static final boolean BRANCH_COMPRESSION = false;
	public static final boolean COLLECT_STATS = false;
	private static final int NEIGHBOR_LEAFS_TO_SCAN = 2;

	private final BlobStore blobStore;
	private final EnumMap<NavigationMode, LeafScanner> scannerMap = new EnumMap<NavigationMode, LeafScanner>(
			NavigationMode.class);

	private static class KeyNotExistentException extends IndexAccessException {
		private static final long serialVersionUID = 1L;
	}

	private class LeafScanner {

		protected static final int MAX_NEIGHBORS = 4;
		protected final int neighborLeafsToScan;
		protected ScanStats stats;
		private final DefaultScanStats internalStats;

		public LeafScanner(int neighborLeafsToScan) {
			this.neighborLeafsToScan = neighborLeafsToScan;
			this.internalStats = COLLECT_STATS ? new DefaultScanStats(
					neighborLeafsToScan > MAX_NEIGHBORS ? MAX_NEIGHBORS
							: neighborLeafsToScan) : null;
			this.stats = this.internalStats;
		}

		public LeafScanner() {
			this.neighborLeafsToScan = 0;
			this.internalStats = COLLECT_STATS ? new DefaultScanStats(
					neighborLeafsToScan > MAX_NEIGHBORS ? MAX_NEIGHBORS
							: neighborLeafsToScan) : null;
			this.stats = this.internalStats;
		}

		public String printStats() {
			return stats.printStats();
		}

		protected ScanResult hintPageScanFailed(Tx tx, PageID rootPageID,
				Leaf leaf, NavigationMode navMode, XTCdeweyID key,
				boolean forUpdate, NavigationStatus navStatus)
				throws IndexAccessException {

			try {

				// check neighbors

				for (int i = 0; i < neighborLeafsToScan; i++) {

					PageID nextPageID = leaf.getNextPageID();
					if (nextPageID == null) {
						if (COLLECT_STATS && i <= MAX_NEIGHBORS)
							if (i == 0) {
								internalStats.hintPageHits++;
							} else {
								internalStats.neighborHits[i - 1]++;
							}
						leaf.cleanup();
						throw KEY_NOT_EXISTENT;
					}

					Leaf nextPage = (Leaf) getPage(tx, nextPageID, forUpdate,
							false);
					leaf.cleanup();
					nextPage.assignDeweyIDBuffer(leaf);
					leaf = nextPage;

					XTCdeweyID highKey = leaf.getHighKey();

					if (highKey != null && navMode.isAfterHighKey(key, highKey)) {
						// current leaf page can be skipped
						navStatus = NavigationStatus.AFTER_LAST;
					} else {
						// check current page
						navStatus = leaf.navigateContextFree(key, navMode);

						if (navStatus == NavigationStatus.FOUND) {
							if (COLLECT_STATS && i < MAX_NEIGHBORS) {
								internalStats.neighborHits[i]++;
							}
							return new ScanResult(leaf);
						} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
							if (COLLECT_STATS && i < MAX_NEIGHBORS) {
								internalStats.neighborHits[i]++;
							}
							leaf.cleanup();
							throw KEY_NOT_EXISTENT;
						}
					}
				}

				// target node not found in neighbors

				if (leaf.getNextPageID() == null) {
					// no need for an additional index access
					if (COLLECT_STATS) {
						if (neighborLeafsToScan > 0) {
							internalStats.neighborHits[neighborLeafsToScan - 1]++;
						} else {
							internalStats.hintPageHits++;
						}
					}
					leaf.cleanup();
					throw KEY_NOT_EXISTENT;
				} else {
					if (COLLECT_STATS) {
						internalStats.neighborFails++;
					}
					leaf.cleanup();
					return new ScanResult();
				}

			} catch (IndexOperationException e) {
				if (leaf != null) {
					try {
						leaf.cleanup();
					} catch (Exception ex) {
					}
				}
				throw new IndexAccessException(e,
						"Error during hint page scan.");
			}

		}

		protected ScanResult indexAccessScan(Tx tx, PageID rootPageID,
				Leaf leaf, NavigationMode navMode, XTCdeweyID key,
				boolean forUpdate) throws IndexAccessException {
			// scan all leaf pages sequentially until the target node is found

			try {

				NavigationStatus navStatus = null;

				while (true) {
					XTCdeweyID highKey = leaf.getHighKey();

					if (highKey != null && navMode.isAfterHighKey(key, highKey)) {
						// current leaf page can be skipped
						navStatus = NavigationStatus.AFTER_LAST;
					} else {
						// check current page
						navStatus = leaf.navigateContextFree(key, navMode);

						if (navStatus == NavigationStatus.FOUND) {
							return new ScanResult(leaf);
						} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
							leaf.cleanup();
							throw KEY_NOT_EXISTENT;
						}
					}

					PageID nextPageID = leaf.getNextPageID();
					if (nextPageID == null) {
						leaf.cleanup();
						throw KEY_NOT_EXISTENT;
					}

					Leaf nextPage = (Leaf) getPage(tx, nextPageID, forUpdate,
							false);
					leaf.cleanup();
					nextPage.assignDeweyIDBuffer(leaf);
					leaf = nextPage;
				}

			} catch (IndexOperationException e) {
				if (leaf != null) {
					try {
						leaf.cleanup();
					} catch (Exception ex) {
					}
				}
				throw new IndexAccessException(e,
						"Error during leaf page scan over index.");
			}
		}

		public ScanResult scan(Tx tx, PageID rootPageID, Leaf leaf,
				NavigationMode navMode, XTCdeweyID key, boolean forUpdate,
				boolean indexAccess) throws IndexAccessException {

			if (!indexAccess) {
				// hintPage scan
				if (COLLECT_STATS) {
					stats.hintPageScan();
				}

				NavigationStatus navStatus = leaf.navigate(navMode);

				if (navStatus == NavigationStatus.FOUND) {
					if (COLLECT_STATS) {
						stats.hintPageHit();
					}
					return new ScanResult(leaf);
				} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
					// searched key does not exist
					if (COLLECT_STATS) {
						stats.hintPageHit();
					}
					leaf.cleanup();
					throw KEY_NOT_EXISTENT;
				} else {
					// requested node is possibly located in the previous or the
					// next page(s)
					return hintPageScanFailed(tx, rootPageID, leaf, navMode,
							key, forUpdate, navStatus);
				}
			} else {
				if (COLLECT_STATS) {
					stats.indexAccess();
				}
				return indexAccessScan(tx, rootPageID, leaf, navMode, key,
						forUpdate);
			}
		}

	}

	private class LastChildScanner extends LeafScanner {

		private final LastChildScanStats internalStats;

		public LastChildScanner(int neighborLeafsToScan) {
			super(neighborLeafsToScan);
			this.internalStats = COLLECT_STATS ? new LastChildScanStats(
					neighborLeafsToScan > MAX_NEIGHBORS ? MAX_NEIGHBORS
							: neighborLeafsToScan) : null;
			this.stats = this.internalStats;
		}

		public LastChildScanner() {
			super();
			this.internalStats = COLLECT_STATS ? new LastChildScanStats(
					neighborLeafsToScan > MAX_NEIGHBORS ? MAX_NEIGHBORS
							: neighborLeafsToScan) : null;
			this.stats = this.internalStats;
		}

		@Override
		protected ScanResult indexAccessScan(Tx tx, PageID rootPageID,
				Leaf leaf, NavigationMode navMode, XTCdeweyID key,
				boolean forUpdate) throws IndexAccessException {
			NavigationStatus navStatus = null;
			Leaf lastPage = null;

			try {

				while (true) {
					XTCdeweyID highKey = leaf.getHighKey();

					if (highKey != null && navMode.isAfterHighKey(key, highKey)) {
						// current leaf page can be skipped
						navStatus = NavigationStatus.AFTER_LAST;

						// release last page
						if (lastPage != null) {
							lastPage.cleanup();
							leaf.assignDeweyIDBuffer(lastPage);
							lastPage = null;
						}

						PageID nextPageID = leaf.getNextPageID();
						lastPage = leaf;
						leaf = (Leaf) getPage(tx, nextPageID, forUpdate, false);

					} else {

						XTCdeweyID lowKey = leaf.getLowKey();

						// check whether last or current page should be used for
						// searching
						if (lastPage != null) {
							if (lowKey != null
									&& navMode.isAfterHighKey(key, lowKey)) {
								// use current page
								lastPage.cleanup();
								leaf.assignDeweyIDBuffer(lastPage);
								lastPage = null;
							} else {
								// use last page
								leaf.cleanup();
								leaf = lastPage;
								lowKey = leaf.getLowKey();
							}
						} else if (lowKey == null
								|| !navMode.isAfterHighKey(key, lowKey)) {
							// index led us to a wrong page -> previous page is
							// correct
							PageID previousPageID = leaf.getPrevPageID();
							Leaf current = leaf;
							current.cleanup();
							leaf = (Leaf) getPage(tx, previousPageID,
									forUpdate, false);
							leaf.assignDeweyIDBuffer(current);
							return indexAccessScan(tx, rootPageID, leaf,
									navMode, key, forUpdate);
						}

						// check page
						navStatus = leaf.navigateContextFree(key, navMode);

						if (navStatus == NavigationStatus.FOUND) {
							if (COLLECT_STATS) {
								internalStats.indexAccessHit++;
							}
							return new ScanResult(leaf);
						} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
							if (COLLECT_STATS) {
								internalStats.indexAccessHit++;
							}
							leaf.cleanup();
							throw KEY_NOT_EXISTENT;
						} else if (navStatus == NavigationStatus.POSSIBLY_FOUND) {
							if (COLLECT_STATS) {
								internalStats.indexAccessHit++;
							}
							// found node is definitely the last child
							return new ScanResult(leaf);
						} else {
							// determine last child's DeweyID
							XTCdeweyID targetDeweyID = lowKey.getAncestor(
									key.getLevel() + 1, key);

							if (targetDeweyID == null
									|| targetDeweyID.isAttributeRoot()) {
								if (COLLECT_STATS) {
									internalStats.indexAccessHit++;
								}
								// no child exists
								leaf.cleanup();
								throw KEY_NOT_EXISTENT;
							} else {
								if (COLLECT_STATS) {
									internalStats.indexAccessDeweyIDFound++;
								}
								leaf.cleanup();
								return new ScanResult(targetDeweyID);
							}
						}
					}
				}

			} catch (IndexOperationException e) {
				if (lastPage != null) {
					try {
						lastPage.cleanup();
					} catch (Exception ex) {
					}
				}
				if (leaf != null) {
					try {
						leaf.cleanup();
					} catch (Exception ex) {
					}
				}
				throw new IndexAccessException(e,
						"Error during leaf page scan over index.");
			}
		}

		/**
		 * Cleans up the (not null) leafs in the array except the page with the
		 * specified index.
		 */
		private void cleanupPages(Leaf[] leafs, int skipIndex) {
			for (int i = 0; i < leafs.length; i++) {
				if (leafs[i] != null) {
					if (i != skipIndex) {
						try {
							leafs[i].cleanup();
						} catch (Exception e) {
						}
					}
				} else {
					return;
				}
			}
		}

		@Override
		protected ScanResult hintPageScanFailed(Tx tx, PageID rootPageID,
				Leaf leaf, NavigationMode navMode, XTCdeweyID key,
				boolean forUpdate, NavigationStatus navStatus)
				throws IndexAccessException {

			// check whether inspection of neighbor pages is necessary at all
			XTCdeweyID hintPageHighKey = leaf.getHighKey();
			if (hintPageHighKey == null
					|| !navMode.isAfterHighKey(key, hintPageHighKey)) {
				// there is no next page or next page does not need to be
				// inspected
				if (COLLECT_STATS) {
					internalStats.hintPageHits++;
				}
				if (navStatus == NavigationStatus.POSSIBLY_FOUND) {
					return new ScanResult(leaf);
				} else {
					leaf.cleanup();
					throw KEY_NOT_EXISTENT;
				}
			}

			DeweyIDBuffer deweyIDBuffer = leaf.getDeweyIDBuffer();
			Leaf[] lastPages = new Leaf[neighborLeafsToScan];
			BracketContext hintPageContext = (navStatus == NavigationStatus.POSSIBLY_FOUND) ? leaf
					.getContext() : null;

			try {

				// check neighbors

				for (int i = 0; i < neighborLeafsToScan; i++) {

					PageID nextPageID = leaf.getNextPageID();
					// assert(nextPageID != null);

					lastPages[i] = leaf;
					leaf.deassignDeweyIDBuffer();
					leaf = (Leaf) getPage(tx, nextPageID, forUpdate, false);

					XTCdeweyID highKey = leaf.getHighKey();

					if (highKey != null && navMode.isAfterHighKey(key, highKey)) {
						// current leaf page can be skipped
						navStatus = NavigationStatus.AFTER_LAST;
						leaf.assignDeweyIDBuffer(deweyIDBuffer);
					} else {

						boolean skipLastPage = false;

						XTCdeweyID lowKey = leaf.getLowKey();
						// lowKey can be null, if this is the last leaf page

						// check whether last or current page should be used for
						// searching
						if (lowKey != null
								&& navMode
										.isAfterHighKey(key, leaf.getLowKey())) {
							// use current page
							leaf.assignDeweyIDBuffer(deweyIDBuffer);
						} else {
							// use last page
							leaf.cleanup();
							leaf = lastPages[i];
							leaf.assignDeweyIDBuffer(deweyIDBuffer);
							lastPages[i] = null;
							skipLastPage = true;
							lowKey = leaf.getLowKey();

							if (i == 0) {
								// we are in the first neighbor page ->
								// lastChild is either in the hintPage or it
								// does not exist
								if (COLLECT_STATS) {
									internalStats.neighborHits[i]++;
								}
								if (navStatus == NavigationStatus.POSSIBLY_FOUND) {
									leaf.setContext(hintPageContext);
									return new ScanResult(leaf);
								} else {
									leaf.cleanup();
									throw KEY_NOT_EXISTENT;
								}
							}
						}

						// check current page
						navStatus = leaf.navigateContextFree(key, navMode);

						if (navStatus == NavigationStatus.FOUND) {
							if (COLLECT_STATS) {
								internalStats.neighborHits[i]++;
							}
							cleanupPages(lastPages, -1);
							return new ScanResult(leaf);
						} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
							if (COLLECT_STATS) {
								internalStats.neighborHits[i]++;
							}
							cleanupPages(lastPages, -1);
							leaf.cleanup();
							throw KEY_NOT_EXISTENT;
						} else if (navStatus == NavigationStatus.POSSIBLY_FOUND) {
							// found node is definitely the last child
							if (COLLECT_STATS) {
								internalStats.neighborHits[i]++;
							}
							cleanupPages(lastPages, -1);
							return new ScanResult(leaf);
						} else {
							// next sibling is in the current page, but the last
							// child is not
							if (i == 0) {
								// we are in the first neighbor page ->
								// lastChild is either in the hintPage or it
								// does not exist
								if (COLLECT_STATS) {
									internalStats.neighborHits[i]++;
								}
								if (hintPageContext == null) {
									lastPages[0].cleanup();
									leaf.cleanup();
									throw KEY_NOT_EXISTENT;
								} else {
									leaf.cleanup();
									leaf = lastPages[0];
									leaf.assignDeweyIDBuffer(deweyIDBuffer);
									leaf.setContext(hintPageContext);
									return new ScanResult(leaf);
								}
							}

							// determine last child's DeweyID
							XTCdeweyID targetDeweyID = lowKey.getAncestor(
									key.getLevel() + 1, key);

							if (targetDeweyID == null
									|| targetDeweyID.isAttributeRoot()) {
								// no child exists
								if (COLLECT_STATS) {
									internalStats.neighborHits[i]++;
								}
								cleanupPages(lastPages, -1);
								leaf.cleanup();
								throw KEY_NOT_EXISTENT;
							} else {
								if (COLLECT_STATS) {
									internalStats.neighborDeweyIDFound[i]++;
								}
								leaf.cleanup();

								// look for the targetDeweyID in the lastPages
								Leaf currentLeaf = null;
								for (int j = i - (skipLastPage ? 1 : 0); j > 0; j--) {
									currentLeaf = lastPages[j];
									if (targetDeweyID
											.compareDivisions(currentLeaf
													.getLowKey()) >= 0) {
										// lastChild lies in the j-th lastPage
										cleanupPages(lastPages, j);
										currentLeaf
												.assignDeweyIDBuffer(deweyIDBuffer);
										currentLeaf.navigateContextFree(
												targetDeweyID,
												NavigationMode.TO_KEY);
										return new ScanResult(currentLeaf);
									}
								}

								// lastChild lies in the hintPage
								currentLeaf = lastPages[0];
								cleanupPages(lastPages, 0);
								currentLeaf.assignDeweyIDBuffer(deweyIDBuffer);
								currentLeaf.setContext(hintPageContext);
								return new ScanResult(currentLeaf);
							}
						}
					}
				}

				// target node not found in neighbors
				cleanupPages(lastPages, -1);
				leaf.cleanup();
				if (COLLECT_STATS) {
					internalStats.neighborFails++;
				}
				return new ScanResult();

			} catch (IndexOperationException e) {
				cleanupPages(lastPages, -1);
				if (leaf != null) {
					try {
						leaf.cleanup();
					} catch (Exception ex) {
					}
				}
				throw new IndexAccessException(e,
						"Error during hint page scan.");
			}
		}
	}

	private class PreviousSiblingScanner extends LeafScanner {

		private final PreviousSiblingScanStats internalStats;

		public PreviousSiblingScanner() {
			super();
			this.internalStats = COLLECT_STATS ? new PreviousSiblingScanStats()
					: null;
			this.stats = internalStats;
		}

		@Override
		protected ScanResult indexAccessScan(Tx tx, PageID rootPageID,
				Leaf leaf, NavigationMode navMode, XTCdeweyID key,
				boolean forUpdate) throws IndexAccessException {
			NavigationStatus navStatus = null;
			Leaf lastPage = null;

			try {

				while (true) {
					XTCdeweyID highKey = leaf.getHighKey();

					if (highKey != null && navMode.isAfterHighKey(key, highKey)) {
						// current leaf page can be skipped
						navStatus = NavigationStatus.AFTER_LAST;

						// release last page
						if (lastPage != null) {
							lastPage.cleanup();
							leaf.assignDeweyIDBuffer(lastPage);
							lastPage = null;
						}

						PageID nextPageID = leaf.getNextPageID();
						lastPage = leaf;
						leaf = (Leaf) getPage(tx, nextPageID, forUpdate, false);

					} else {

						XTCdeweyID lowKey = leaf.getLowKey();

						// check whether last or current page should be used for
						// searching
						if (lastPage != null) {
							if (navMode.isAfterHighKey(key, lowKey)) {
								// use current page
								lastPage.cleanup();
								leaf.assignDeweyIDBuffer(lastPage);
								lastPage = null;
							} else {
								// use last page
								leaf.cleanup();
								leaf = lastPage;
								lowKey = leaf.getLowKey();
							}
						} else if (!navMode.isAfterHighKey(key, lowKey)) {
							// index led us to a wrong page -> previous page is
							// correct
							PageID previousPageID = leaf.getPrevPageID();
							Leaf current = leaf;
							current.cleanup();
							leaf = (Leaf) getPage(tx, previousPageID,
									forUpdate, false);
							leaf.assignDeweyIDBuffer(current);
							return indexAccessScan(tx, rootPageID, leaf,
									navMode, key, forUpdate);
						}

						// check page
						navStatus = leaf.navigateContextFree(key, navMode);

						if (navStatus == NavigationStatus.FOUND) {
							if (COLLECT_STATS) {
								internalStats.indexAccessHit++;
							}
							return new ScanResult(leaf);
						} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
							if (COLLECT_STATS) {
								internalStats.indexAccessHit++;
							}
							leaf.cleanup();
							throw KEY_NOT_EXISTENT;
						} else {
							XTCdeweyID targetDeweyID = lowKey.getAncestor(key
									.getLevel());
							if (targetDeweyID.isAttributeRoot()) {
								if (COLLECT_STATS) {
									internalStats.indexAccessHit++;
								}
								leaf.cleanup();
								throw KEY_NOT_EXISTENT;
							}

							if (COLLECT_STATS) {
								internalStats.indexAccessDeweyIDFound++;
							}
							leaf.cleanup();
							return new ScanResult(targetDeweyID);
						}
					}
				}

			} catch (IndexOperationException e) {
				if (lastPage != null) {
					try {
						lastPage.cleanup();
					} catch (Exception ex) {
					}
				}
				if (leaf != null) {
					try {
						leaf.cleanup();
					} catch (Exception ex) {
					}
				}
				throw new IndexAccessException(e,
						"Error during leaf page scan over index.");
			}
		}

		@Override
		protected ScanResult hintPageScanFailed(Tx tx, PageID rootPageID,
				Leaf leaf, NavigationMode navMode, XTCdeweyID key,
				boolean forUpdate, NavigationStatus navStatus)
				throws IndexAccessException {
			// determine previous sibling's DeweyID
			XTCdeweyID lowKey = leaf.getLowKey();
			if (lowKey.compareDivisions(key) < 0) {

				XTCdeweyID targetDeweyID = lowKey.getAncestor(key.getLevel());
				if (targetDeweyID.isAttributeRoot()) {
					if (COLLECT_STATS) {
						internalStats.hintPageHits++;
					}
					leaf.cleanup();
					throw KEY_NOT_EXISTENT;
				}

				if (COLLECT_STATS) {
					internalStats.hintPageDeweyIDFound++;
				}
				leaf.cleanup();
				return new ScanResult(targetDeweyID);
			} else {
				// access via index
				if (COLLECT_STATS) {
					internalStats.hintPageFails++;
				}
				leaf.cleanup();
				return new ScanResult();
			}
		}
	}

	/**
	 * This PostCommitHook finishes the subtree deletion by removing the inner
	 * leaf pages.
	 */
	private class DeletePageHook implements PostCommitHook {

		private final PageID rootPageID;
		private final List<PageID> pageIDs;

		public DeletePageHook(PageID rootPageID, List<PageID> pageIDs) {
			this.rootPageID = rootPageID;
			this.pageIDs = pageIDs;
		}

		@Override
		public void execute(Tx tx) throws ServerException {
			Buffer buffer = bufferMgr.getBuffer(rootPageID);
			List<PageID> exceptionPageIDs = null;

			for (PageID pageID : pageIDs) {
				try {
					// TODO: log page deletion?
					buffer.deletePage(tx, pageID, false, -1);
				} catch (BufferException e) {
					if (exceptionPageIDs == null) {
						exceptionPageIDs = new ArrayList<PageID>();
					}
					exceptionPageIDs.add(pageID);
				}
			}

			if (exceptionPageIDs != null) {
				throw new ServerException(String.format(
						"Error deleting pages %s.", exceptionPageIDs));
			}
		}
	}

	public BracketTree(BufferMgr bufferMgr) {
		super(bufferMgr);
		blobStore = new SimpleBlobStore(bufferMgr);
		initializeScannerMap();
	}

	private void initializeScannerMap() {
		// mapping between navigation mode and used leaf scanner
		scannerMap.put(NavigationMode.FIRST_CHILD, new LeafScanner(
				Integer.MAX_VALUE));
		scannerMap.put(NavigationMode.LAST_CHILD, new LastChildScanner(
				NEIGHBOR_LEAFS_TO_SCAN));
		scannerMap.put(NavigationMode.NEXT_ATTRIBUTE, new LeafScanner(
				Integer.MAX_VALUE));
		scannerMap.put(NavigationMode.NEXT_SIBLING, new LeafScanner(
				NEIGHBOR_LEAFS_TO_SCAN));
		scannerMap.put(NavigationMode.PARENT, new LeafScanner());
		scannerMap.put(NavigationMode.PREVIOUS_SIBLING,
				new PreviousSiblingScanner());
		scannerMap.put(NavigationMode.TO_INSERT_POS, new LeafScanner());
		scannerMap.put(NavigationMode.TO_KEY, new LeafScanner());
	}

	protected long logDummyCLR(Tx tx, long undoNextLSN)
			throws IndexOperationException {
		try {
			long lsn = tx.logDummyCLR(undoNextLSN);
			return lsn;
		} catch (TxException e) {
			throw new IndexOperationException(e,
					"Could not write dummy CLR to log.");
		}
	}

	protected void undo(Tx tx, long rememberedLSN) {
		try {
			tx.undo(rememberedLSN);
		} catch (TxException e) {
			log.error(String.format(
					"Could not undo changes of %s back to LSN %s.", tx,
					rememberedLSN), e);
		}
	}

	public Leaf descendToPosition(Tx tx, PageID rootPageID,
			NavigationMode navMode, XTCdeweyID key,
			DeweyIDBuffer deweyIDBuffer, LeafScanner scanner, boolean forUpdate)
			throws IndexAccessException {
		Leaf leaf = descend(tx, rootPageID, navMode.getSearchMode(), navMode
				.getSearchKey(key).toBytes(), forUpdate);
		leaf.assignDeweyIDBuffer(deweyIDBuffer);
		ScanResult scanRes = scanner.scan(tx, rootPageID, leaf, navMode, key,
				forUpdate, true);

		if (scanRes.nodeFound) {
			return scanRes.resultLeaf;
		} else {
			return descendToPosition(tx, rootPageID, NavigationMode.TO_KEY,
					scanRes.targetDeweyID, deweyIDBuffer, scanner, forUpdate);
		}
	}

	private Leaf descend(Tx tx, PageID rootPageID, SearchMode searchMode,
			byte[] key, boolean forUpdate) throws IndexAccessException {
		return (Leaf) descend(tx, rootPageID, searchMode, key, 0, forUpdate);
	}

	private BPContext descend(Tx tx, PageID rootPageID, SearchMode searchMode,
			byte[] key, int targetHeight, boolean forUpdate)
			throws IndexAccessException {
		PageID pageID = rootPageID;
		BPContext page = null;

		try {
			while (true) {
				if (log.isTraceEnabled()) {
					log.trace(String.format("Descending to page %s.", pageID));
				}

				int retries = 0;
				while (true) {
					try {
						page = getPage(tx, pageID, forUpdate, forUpdate);
						break;
					} catch (IndexOperationException e) {
						// If we could not access the index root, the index does
						// not exist at all.
						// This is generally a severe error.
						// If this happens for a non-root page, it is likely
						// because we tried to
						// descend to a deleted page and we should simply try to
						// restart the traversal.
						// Note that we could perform additional checks to
						// ensure that a page deletion
						// is the real cause for this exception. However, this
						// case is rare anyway and
						// we simply save us the programming time to do it.
						if ((page.getPageID().equals(rootPageID))
								|| (++retries > 3)) {
							throw new IndexAccessException(e,
									"Error fetching index page %s.", pageID);
						}
						pageID = rootPageID;
					}
				}

				if (page.isLeaf()) {
					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Reached leaf page %s searching for %s %s.",
								page, searchMode, Field.DEWEYID.toString(key)));
						log.trace(page.dump("leaf page"));
					}

					if (forUpdate) {
						page.upX();
					}
					return page;
				} else if (page.getHeight() == targetHeight) {
					if (log.isTraceEnabled()) {
						log.trace(String
								.format("Reached branch page %s with height %s for search key %s.",
										page, searchMode,
										Field.DEWEYID.toString(key)));
						log.trace(page.dump("branch page"));
					}

					if (forUpdate) {
						page.upX();
					}
					return page;
				}

				// page is a branch
				Branch branchPage = (Branch) page;

				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Passing branch page %s and searching for %s %s.",
							branchPage, Field.DEWEYID.toString(key)));
					log.trace(page.dump("tree page"));
				}

				if (forUpdate) {
					page.downS();
				}

				PageID childPageID = branchPage.searchNextPageID(searchMode,
						key);

				// perform side-steps while we keep the latch on the current
				// page
				while ((branchPage.getPosition() == branchPage.getEntryCount())
						&& (!branchPage.isLastInLevel())
						&& (childPageID.equals(branchPage.getValueAsPageID()))) {
					Branch next = (Branch) getPage(tx, childPageID, false,
							false);
					branchPage.cleanup();
					branchPage = next;
					childPageID = branchPage.searchNextPageID(searchMode, key);
				}

				branchPage.cleanup();
				pageID = childPageID;
			}
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Error inspecting index page %s",
					pageID);
		}
	}

	protected void readLockEntry(Tx tx, PageID rootPageID, BPContext page,
			byte[] key, byte[] value) throws IndexOperationException {
	}

	protected void updateLockEntry(Tx tx, PageID rootPageID, BPContext page,
			byte[] key, byte[] value) throws IndexOperationException {
	}

	protected void downgradeLockEntry(Tx tx, PageID rootPageID, BPContext page,
			byte[] key, byte[] value) throws IndexOperationException {
	}

	protected Leaf moveNext(Tx tx, PageID rootPageID, Leaf page,
			OpenMode openMode) throws IndexAccessException {
		byte[] currentKey = null;
		byte[] currentValue = null;

		try {
			if (openMode != OpenMode.LOAD) {
				if (openMode.forUpdate()) {
					downgradeLockEntry(tx, rootPageID, page, currentKey,
							currentValue);
				}
			}

			if (page.moveNext()) {
				if (openMode != OpenMode.LOAD) {
					if (openMode.forUpdate()) {
						updateLockEntry(tx, rootPageID, page, page.getKey()
								.toBytes(), page.getValue());
					} else {
						readLockEntry(tx, rootPageID, page, page.getKey()
								.toBytes(), page.getValue());
					}
				}
				return page;
			}

			if (page.isLastInLevel()) {
				if (log.isTraceEnabled()) {
					log.trace("Reached end of index.");
				}

				// if (openMode != OpenMode.LOAD)
				// {
				// // lock EOF
				// if (openMode.forUpdate())
				// {
				// updateLockEntry(tx, rootPageID, page,
				// page.getKey().toBytes(), page.getValue());
				// }
				// else
				// {
				// readLockEntry(tx, rootPageID, page, page.getKey().toBytes(),
				// page.getValue());
				// }
				// }

				page.cleanup();
				return null;
			}

			if (log.isTraceEnabled()) {
				log.trace(String
						.format("Reached end of current page %s. Attempting to proceed to next page %s.",
								page, page.getNextPageID()));
			}

			Leaf next = (Leaf) getPage(tx, page.getNextPageID(),
					openMode.forUpdate(), false);

			if (log.isTraceEnabled()) {
				log.trace(String.format("Switching to next page %s.", next));
			}
			page.cleanup();
			try {
				next.moveFirst();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// lock key or EOF
			if (openMode != OpenMode.LOAD) {
				if (openMode.forUpdate()) {
					updateLockEntry(tx, rootPageID, page, page.getKey()
							.toBytes(), page.getValue());
				} else {
					readLockEntry(tx, rootPageID, page,
							page.getKey().toBytes(), page.getValue());
				}
			}

			return next;
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not move to next entry");
		}
	}

	protected Branch moveNext(Tx tx, PageID rootPageID, Branch page,
			OpenMode openMode) throws IndexAccessException {
		byte[] currentKey = null;
		byte[] currentValue = null;

		try {
			if (openMode != OpenMode.LOAD) {
				if (openMode.forUpdate()) {
					downgradeLockEntry(tx, rootPageID, page, currentKey,
							currentValue);
				}
			}

			if (page.moveNext()) {
				if (openMode != OpenMode.LOAD) {
					if (openMode.forUpdate()) {
						updateLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					} else {
						readLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					}
				}
				return page;
			}

			if (page.isLastInLevel()) {
				if (log.isTraceEnabled()) {
					log.trace("Reached end of index.");
				}

				if (openMode != OpenMode.LOAD) {
					// lock EOF
					if (openMode.forUpdate()) {
						updateLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					} else {
						readLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					}
				}

				return page;
			}

			if (log.isTraceEnabled()) {
				log.trace(String
						.format("Reached end of current page %s. Attempting to proceed to next page %s.",
								page, page.getValueAsPageID()));
			}

			Branch next = (Branch) getPage(tx, page.getValueAsPageID(),
					openMode.forUpdate(), false);

			if (log.isTraceEnabled()) {
				log.trace(String.format("Switching to next page %s.", next));
			}
			page.cleanup();
			try {
				next.moveFirst();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// lock key or EOF
			if (openMode != OpenMode.LOAD) {
				if (openMode.forUpdate()) {
					updateLockEntry(tx, rootPageID, page, page.getKey(),
							page.getValue());
				} else {
					readLockEntry(tx, rootPageID, page, page.getKey(),
							page.getValue());
				}
			}

			return next;
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not move to next entry");
		}
	}

	protected Leaf movePrevious(Tx tx, PageID rootPageID, Leaf page,
			OpenMode openMode) throws IndexAccessException {
		page.cleanup();
		throw new IndexAccessException("Not implemented yet");
	}

	public Leaf insertIntoLeafBulk(Tx tx, PageID rootPageID, Leaf leaf,
			XTCdeweyID insertKey, byte[] insertValue, int ancestorsToInsert,
			BulkInsertContext bulkContext, boolean logged, long undoNextLSN)
			throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				log.trace(leaf.dump(String.format(
						"Leaf Page %s for insert of (%s, %s) at %s", leaf,
						insertKey, Field.EL_REC.toString(insertValue),
						leaf.getOffset())));
			}

			/*
			 * Optimistically try to insert record in page saving the
			 * computation of required space. If this fails we will have to
			 * perform a split
			 */
			while (!leaf.insertRecordAfter(insertKey, insertValue,
					ancestorsToInsert, logged, undoNextLSN)) {
				if (log.isTraceEnabled()) {
					log.trace(String
							.format("Splitting leaf page %s for insert of (%s, %s) at %s",
									leaf, insertKey,
									Field.EL_REC.toString(insertValue),
									leaf.getOffset()));
				}

				XTCdeweyID ancestorInsertKey = BracketPage.getAncestorKey(
						insertKey, ancestorsToInsert);

				// Split and propagate if necessary.
				if (leaf.getPageID().equals(rootPageID)) {
					leaf = splitRootLeaf(tx, rootPageID, leaf,
							ancestorInsertKey, false, logged);
					// leftmost page / current left page has changed due to root
					// split
					bulkContext.initialize(leaf);
				} else {
					leaf = splitNonRootLeafBulk(tx, rootPageID, leaf,
							ancestorInsertKey, bulkContext, logged);
				}

				if (log.isTraceEnabled()) {
					log.trace(leaf.dump(String
							.format("Splitted leaf page %s before insert of (%s, %s) at %s",
									leaf, insertKey,
									Field.EL_REC.toString(insertValue),
									leaf.getOffset())));
				}
			}

			if (log.isTraceEnabled()) {
				log.trace(leaf.dump("Leaf Page after insert"));
			}

			tx.getStatistics().increment(TxStats.BTREE_INSERTS);

			return leaf;
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e, "Could not log record insertion.");
		}
	}

	public Leaf insertIntoLeaf(Tx tx, PageID rootPageID, Leaf leaf,
			XTCdeweyID key, byte[] value, int ancestorsToInsert,
			boolean logged, long undoNextLSN) throws IndexAccessException {
		return insertIntoLeafPage(tx, rootPageID, leaf, key, value,
				ancestorsToInsert, false, logged, undoNextLSN);
	}

	protected Leaf insertIntoLeafPage(Tx tx, PageID rootPageID, Leaf page,
			XTCdeweyID insertKey, byte[] insertValue, int ancestorsToInsert,
			boolean isStructureModification, boolean logged, long undoNextLSN)
			throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				log.trace(page.dump(String.format(
						"Page %s for insert of (%s, %s) at %s", page,
						insertKey, Field.EL_REC.toString(insertValue),
						page.getOffset())));
			}

			/*
			 * Optimistically try to insert record in page saving the
			 * computation of required space. If this fails we will have to
			 * perform a split
			 */
			while (!page.insertRecordAfter(insertKey, insertValue,
					ancestorsToInsert, logged, undoNextLSN)) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Splitting page %s for insert of (%s, %s) at %s",
							page, insertKey,
							Field.EL_REC.toString(insertValue),
							page.getOffset()));
				}

				XTCdeweyID ancestorInsertKey = BracketPage.getAncestorKey(
						insertKey, ancestorsToInsert);

				// Split and propagate if necessary.
				if (page.getPageID().equals(rootPageID)) {
					page = splitRootLeaf(tx, rootPageID, page,
							ancestorInsertKey, false, logged);
				} else {
					page = splitNonRootLeaf(tx, rootPageID, page,
							ancestorInsertKey, false, logged);
				}

				if (log.isTraceEnabled()) {
					log.trace(page.dump(String.format(
							"Splitted page %s before insert of (%s, %s) at %s",
							page, insertKey,
							Field.EL_REC.toString(insertValue),
							page.getOffset())));
				}
			}

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after insert"));
			}

			tx.getStatistics().increment(TxStats.BTREE_INSERTS);

			return page;
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not log record insertion.");
		}
	}

	protected Branch insertIntoBranch(Tx tx, PageID rootPageID, Branch page,
			byte[] insertKey, byte[] insertValue, boolean logged,
			long undoNextLSN) throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				log.trace(page.dump(String.format(
						"Page %s for insert of (%s, %s) at %s", page,
						Field.DEWEYID.toString(insertKey),
						Field.PAGEID.toString(insertValue), page.getPosition())));
			}

			/*
			 * Optimistically try to insert record in page saving the
			 * computation of required space. If this fails we will have to
			 * perform a split
			 */
			while (!page.insert(insertKey, insertValue, logged, undoNextLSN)) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Splitting page %s for insert of (%s, %s) at %s",
							page, Field.DEWEYID.toString(insertKey),
							Field.PAGEID.toString(insertValue),
							page.getPosition()));
				}

				// Split and propagate if necessary.
				if (page.getPageID().equals(rootPageID)) {
					page = splitRoot(tx, rootPageID, page, insertKey,
							insertValue, logged);
				} else {
					page = splitNonRoot(tx, rootPageID, page, insertKey,
							insertValue, logged);
				}

				if (log.isTraceEnabled()) {
					log.trace(page.dump(String.format(
							"Splitted page %s before insert of (%s, %s) at %s",
							page, Field.DEWEYID.toString(insertKey),
							Field.PAGEID.toString(insertValue),
							page.getPosition())));
				}
			}

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after insert"));
			}

			tx.getStatistics().increment(TxStats.BTREE_BRANCH_INSERTS);

			return page;
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not log record insertion.");
		}
	}

	public Leaf openInternal(Tx tx, PageID rootPageID, NavigationMode navMode,
			XTCdeweyID key, OpenMode openMode,
			HintPageInformation hintPageInfo, DeweyIDBuffer deweyIDBuffer)
			throws IndexAccessException {

		if (!openMode.doLog()) {
			tx.addFlushHook(rootPageID.getContainerNo());
		}

		Leaf hintLeaf = null;
		if (deweyIDBuffer == null) {
			deweyIDBuffer = new DeweyIDBuffer();
		}

		if (hintPageInfo != null && navMode != NavigationMode.TO_INSERT_POS) {
			try {
				BPContext hintPage = getPage(tx, hintPageInfo.pageID,
						openMode.forUpdate(), false);

				// check LSN of hintPage
				if (hintPage.getLSN() == hintPageInfo.pageLSN) {

					// page has not changed
					hintLeaf = (Leaf) hintPage;
					hintLeaf.assignDeweyIDBuffer(deweyIDBuffer);

					if (navMode == NavigationMode.TO_KEY) {
						// try to find the reference key
						boolean useHintPage = false;
						XTCdeweyID hintLeafHighKey = hintLeaf.getHighKey();
						if (hintLeafHighKey == null
								|| key.compareDivisions(hintLeaf.getHighKey()) < 0) {
							NavigationStatus navStatus = hintLeaf
									.navigateContextFree(key,
											NavigationMode.TO_KEY);
							if (navStatus == NavigationStatus.FOUND) {
								useHintPage = true;
							}
						}
						if (!useHintPage) {
							hintLeaf.cleanup();
							hintLeaf = null;
						}
					} else {
						hintLeaf.setContext(key, hintPageInfo.currentOffset);
					}

				} else if (hintPage.isLeaf()
						&& hintPage.getRootPageID().equals(rootPageID)) {

					// page has changed, but is still a leaf of the correct
					// document
					hintLeaf = (Leaf) hintPage;
					hintLeaf.assignDeweyIDBuffer(deweyIDBuffer);

					// try to find the reference key
					boolean useHintPage = false;
					if (key.compareDivisions(hintLeaf.getHighKey()) < 0) {
						NavigationStatus navStatus = hintLeaf
								.navigateContextFree(key, NavigationMode.TO_KEY);
						if (navStatus == NavigationStatus.FOUND) {
							useHintPage = true;
						}
					}

					if (useHintPage) {
						// refresh hintPageInfo
						hintPageInfo = new HintPageInformation(
								hintLeaf.getPageID(), hintLeaf.getLSN(),
								hintLeaf.getOffset());
					} else {
						// do not use this page as hint page
						hintLeaf.cleanup();
						hintLeaf = null;
					}
				} else {

					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Page %s can not be used for direct access.",
								hintPageInfo.pageID));
					}

					hintPage.cleanup();
				}
			} catch (IndexOperationException e) {
				if (log.isTraceEnabled()) {
					log.trace(String.format("Page %s could not be fixed.",
							hintPageInfo.pageID));
				}
			}
		}

		if (hintLeaf != null) {
			if (navMode == NavigationMode.TO_KEY) {
				// target node already found
				return hintLeaf;
			}
			// hintpage scan
			NavigationStatus navStatus = hintLeaf.navigate(navMode);
			if (navStatus == NavigationStatus.FOUND) {
				return hintLeaf;
			} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
				hintLeaf.cleanup();
				return null;
			}
			// continue navigation
			return navigateAfterHintPageFail(tx, rootPageID, navMode, key,
					openMode, hintLeaf, deweyIDBuffer, navStatus);
		} else {
			return navigate(tx, rootPageID, navMode, key, openMode, hintLeaf,
					deweyIDBuffer);
		}
	}

	protected Leaf loadHintPage(Tx tx, XTCdeweyID key,
			HintPageInformation hintPageInfo, OpenMode openMode,
			DeweyIDBuffer deweyIDBuffer) {

		try {
			Leaf hintLeaf = null;
			BPContext hintPage = getPage(tx, hintPageInfo.pageID,
					openMode.forUpdate(), false);

			// check LSN of hintPage
			if (hintPage.getLSN() == hintPageInfo.pageLSN) {
				// page has not changed
				hintLeaf = (Leaf) hintPage;
				hintLeaf.assignDeweyIDBuffer(deweyIDBuffer);
				hintLeaf.setContext(key, hintPageInfo.currentOffset);
			} else {
				hintPage.cleanup();
			}

			return hintLeaf;

		} catch (IndexOperationException e) {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Page %s could not be fixed.",
						hintPageInfo.pageID));
			}
			return null;
		}
	}

	protected Leaf navigate(Tx tx, PageID rootPageID, NavigationMode navMode,
			XTCdeweyID key, OpenMode openMode, Leaf hintPage,
			DeweyIDBuffer deweyIDBuffer) throws IndexAccessException {

		LeafScanner scanner = scannerMap.get(navMode);

		try {

			if (hintPage != null) {
				ScanResult scanRes = scanner.scan(tx, rootPageID, hintPage,
						navMode, key, openMode.forUpdate(), false);

				if (scanRes.nodeFound) {
					// the specified key is found
					return scanRes.resultLeaf;
				} else if (scanRes.targetDeweyID != null) {
					// look for the target DeweyID
					navMode = NavigationMode.TO_KEY;
					key = scanRes.targetDeweyID;
				}
			}

			// navigation via hintPage was not successful
			// -> use the tree index
			return descendToPosition(tx, rootPageID, navMode, key,
					deweyIDBuffer, scanner, openMode.forUpdate());

		} catch (KeyNotExistentException e) {
			return null;
		}
	}

	protected Leaf navigateAfterHintPageFail(Tx tx, PageID rootPageID,
			NavigationMode navMode, XTCdeweyID key, OpenMode openMode,
			Leaf hintPage, DeweyIDBuffer deweyIDBuffer,
			NavigationStatus navStatus) throws IndexAccessException {

		LeafScanner scanner = scannerMap.get(navMode);

		try {

			ScanResult scanRes = scanner.hintPageScanFailed(tx, rootPageID,
					hintPage, navMode, key, openMode.forUpdate(), navStatus);

			if (scanRes.nodeFound) {
				// the specified key is found
				return scanRes.resultLeaf;
			} else if (scanRes.targetDeweyID != null) {
				// look for the target DeweyID
				navMode = NavigationMode.TO_KEY;
				key = scanRes.targetDeweyID;
			}

			// navigation via hintPage was not successful
			// -> use the tree index
			return descendToPosition(tx, rootPageID, navMode, key,
					deweyIDBuffer, scanner, openMode.forUpdate());

		} catch (KeyNotExistentException e) {
			return null;
		}
	}

	protected Leaf navigateViaIndexAccess(Tx tx, PageID rootPageID,
			NavigationMode navMode, XTCdeweyID key, OpenMode openMode,
			DeweyIDBuffer deweyIDBuffer) throws IndexAccessException {

		LeafScanner scanner = scannerMap.get(navMode);

		try {
			return descendToPosition(tx, rootPageID, navMode, key,
					deweyIDBuffer, scanner, openMode.forUpdate());
		} catch (KeyNotExistentException e) {
			return null;
		}
	}

	protected Leaf splitNonRootLeaf(Tx tx, PageID rootPageID, Leaf left,
			XTCdeweyID key, boolean forUpdate, boolean logged)
			throws IndexAccessException {
		PageID leftPageID = left.getPageID();
		PageID rightPageID = null;
		Leaf right = null;
		Leaf target = null;

		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Splitting non-root leaf page %s.",
						leftPageID));
				log.trace(left.dump("Split Page"));
			}

			// Remember LSN of previously logged action.
			long rememberedLSN = tx.checkPrevLSN();

			// allocate and format new right page
			right = allocateLeaf(tx, -1, left.getUnitID(), rootPageID, logged);
			rightPageID = right.getPageID();

			boolean insertLeft = false;
			byte[] separatorKey = null;

			if (!forUpdate && left.isLast()) {
				// no split necessary
				insertLeft = false;
				left.setHighKey(key, logged, -1);
				separatorKey = key.toBytes();
			} else {

				// actual split
				insertLeft = left.split(right, key, forUpdate, false, logged,
						-1);
				separatorKey = right.getLowKeyBytes();

			}

			// update next pointer if it exists
			PageID nextPageID = left.getNextPageID();

			// chain left page with right page
			left.setNextPageID(right.getPageID(), logged, -1);
			right.setPrevPageID(left.getPageID(), logged, -1);

			if (nextPageID != null) {
				Leaf next = (Leaf) getPage(tx, nextPageID, true, false);

				try {
					right.setNextPageID(next.getPageID(), logged, -1);
					next.setPrevPageID(right.getPageID(), logged, -1);
				} finally {
					next.cleanup();
				}
			}

			// split at this level is complete
			if (log.isTraceEnabled()) {
				log.trace(String.format("Split of non-root page %s completed.",
						left));
				log.trace(left.dump("Left page (splitted)"));
				log.trace(right.dump("Right page (new)"));
			}

			// write dummy CLR to make split at this level invisible to undo
			// processing
			rememberedLSN = logDummyCLR(tx, rememberedLSN);

			// verifySplitPages(left, right, keyType, insertKey, separatorKey,
			// insertPosition, splitPosition, insertIntoLeft,
			// newInsertPosition);

			insertSeparator(tx, rootPageID, separatorKey, leftPageID,
					rightPageID, left.getHeight(), logged);

			// write dummy CLR to make also split propagation invisible to undo
			// processing
			logDummyCLR(tx, rememberedLSN);

			tx.getStatistics().increment(TxStats.BTREE_LEAF_ALLOCATIONS);

			// Free unneeded split page
			if (insertLeft) {
				// unlatch and unfix right split page
				right.cleanup();
				right = null;
				target = left;
				left = null;
			} else {
				// unlatch and unfix right left split page and switch to right
				// page
				left.cleanup();
				left = null;
				target = right;
				right = null;
			}

			return target;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Could not log page split operations.");
		} finally {
			if (left != null) {
				left.cleanup();
			}
			if (right != null) {
				right.cleanup();
			}
		}
	}

	/**
	 * Splits the given leaf page and inserts a sequence of nodes afterwards.
	 * 
	 * @param tx
	 * @param rootPageID
	 * @param left
	 *            the page to split (must point to the correct insertion
	 *            position of the node sequence)
	 * @param nodesToInsert
	 *            the node sequence to insert
	 * @param logged
	 * @return
	 * @throws IndexAccessException
	 */
	protected Leaf splitInsert(Tx tx, PageID rootPageID, Leaf left,
			BracketNodeSequence nodesToInsert, boolean logged)
			throws IndexAccessException {

		PageID leftPageID = left.getPageID();
		PageID rightPageID = null;
		Leaf right = null;
		Leaf middle = null;
		Leaf target = null;
		PageID middlePageID = null;
		XTCdeweyID firstNode = nodesToInsert.getLowKey();
		byte[] oldLeftHighKey = left.getHighKeyBytes();

		try {

			// allocate and format new right page
			right = allocateLeaf(tx, -1, left.getUnitID(), rootPageID, logged);
			rightPageID = right.getPageID();
			tx.getStatistics().increment(TxStats.BTREE_LEAF_ALLOCATIONS);

			boolean insertLeft = false;
			byte[] separatorKey = null;

			if (left.isLast()) {

				// no split necessary
				insertLeft = false;
				separatorKey = firstNode.toBytes();

			} else {

				insertLeft = true;
				// actual split
				moveNodes(left, right, logged);
				right.moveBeforeFirst();
				separatorKey = right.getLowKeyBytes();
			}

			// set left highkey
			if (!left.setHighKeyBytes(separatorKey, logged, -1)) {
				// not enough space for highkey!
				// move at least one record to the right page
				XTCdeweyID beforeInsertKey = null;
				boolean firstRun = true;
				do {
					if (!firstRun) {
						beforeInsertKey = right.getKey();
					}
					left.moveNextToLastRecord();
					moveNodes(left, right, logged);
					separatorKey = right.getLowKeyBytes();
					firstRun = false;
					// try to set highkey again
				} while (!left.setHighKeyBytes(separatorKey, logged, -1));

				// find correct insertion position in right page
				if (beforeInsertKey != null) {
					right.moveBeforeFirst();
					right.navigateContextFree(beforeInsertKey,
							NavigationMode.TO_KEY);
				}
				insertLeft = false;
			}

			// set right highkey
			if (!right.setHighKeyBytes(oldLeftHighKey, logged, -1)) {
				// can not happen, since right page contains at most the data
				// from left page
			}

			if (insertLeft) {
				// insert nodes in left page
				if (!left.insertSequenceAfter(nodesToInsert, logged, -1)) {
					// nodes still do not fit into left page -> try right page
					insertLeft = false;
					separatorKey = firstNode.toBytes();
					if (!left.setHighKeyBytes(separatorKey, logged, -1)) {
						// not enough space for highkey!
						// move at least one record to the right page
						XTCdeweyID beforeInsertKey = null;
						boolean firstRun = true;
						do {
							if (!firstRun) {
								beforeInsertKey = right.getKey();
							}
							left.moveNextToLastRecord();
							moveNodes(left, right, logged);
							separatorKey = right.getLowKeyBytes();
							firstRun = false;
							// try to set highkey again
						} while (!left.setHighKeyBytes(separatorKey, logged, -1));

						// find correct insertion position in right page
						if (beforeInsertKey != null) {
							right.moveBeforeFirst();
							right.navigateContextFree(beforeInsertKey,
									NavigationMode.TO_KEY);
						}
					}
				}
			}

			if (!insertLeft) {
				// insert nodes in right page
				if (!right.insertSequenceAfter(nodesToInsert, logged, -1)) {
					if (right.getEntryCount() == 0) {
						throw new IndexOperationException(
								"Node sequence does not fit into an empty page!");
					}
					// nodes do not fit into right page -> allocate a new one
					middle = allocateLeaf(tx, -1, left.getUnitID(), rootPageID,
							logged);
					middlePageID = middle.getPageID();
					middle.setHighKeyBytes(right.getLowKeyBytes(), logged, -1);
					tx.getStatistics()
							.increment(TxStats.BTREE_LEAF_ALLOCATIONS);
					if (!middle.insertSequenceAfter(nodesToInsert, logged, -1)) {
						throw new IndexOperationException(
								"Node sequence does not fit into an empty page!");
					}
				}
			}

			PageID nextPageID = left.getNextPageID();

			// chain left page with right (or middle) page
			if (middle == null) {
				left.setNextPageID(rightPageID, logged, -1);
				right.setPrevPageID(leftPageID, logged, -1);
			} else {
				left.setNextPageID(middlePageID, logged, -1);
				middle.setPrevPageID(leftPageID, logged, -1);
				middle.setNextPageID(rightPageID, logged, -1);
				right.setPrevPageID(middlePageID, logged, -1);
			}

			// set next page pointer
			if (nextPageID != null) {
				Leaf next = (Leaf) getPage(tx, nextPageID, true, false);

				try {
					right.setNextPageID(next.getPageID(), logged, -1);
					next.setPrevPageID(right.getPageID(), logged, -1);
				} finally {
					try {
						next.cleanup();
					} catch (Exception ex) {
					}
				}
			}

			if (middle == null) {
				// insert separator
				insertSeparator(tx, rootPageID, separatorKey, leftPageID,
						rightPageID, 0, logged);
			} else {
				// insert two separators
				insertSeparator(tx, rootPageID, separatorKey, leftPageID,
						middlePageID, 0, logged);
				insertSeparator(tx, rootPageID, middle.getHighKeyBytes(),
						middlePageID, rightPageID, 0, logged);
			}

			// Free unneeded split pages
			if (middle != null) {
				left.cleanup();
				left = null;
				right.cleanup();
				right = null;
				target = middle;
				middle = null;
			} else if (insertLeft) {
				right.cleanup();
				right = null;
				target = left;
				left = null;
			} else {
				left.cleanup();
				left = null;
				target = right;
				right = null;
			}

			return target;

		} catch (IndexOperationException e) {
			throw new IndexAccessException(e);
		} finally {
			if (left != null) {
				try {
					left.cleanup();
				} catch (Exception ex) {
				}
			}
			if (right != null) {
				try {
					right.cleanup();
				} catch (Exception ex) {
				}
			}
			if (middle != null) {
				try {
					middle.cleanup();
				} catch (Exception ex) {
				}
			}
		}
	}

	protected Leaf splitNonRootLeafBulk(Tx tx, PageID rootPageID, Leaf left,
			XTCdeweyID key, BulkInsertContext bulkContext, boolean logged)
			throws IndexAccessException {
		PageID leftPageID = left.getPageID();
		Leaf right = bulkContext.getCurrentRightPage();

		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Splitting non-root leaf page %s.",
						leftPageID));
				log.trace(left.dump("Split Page"));
			}

			// Remember LSN of previously logged action.
			long rememberedLSN = tx.checkPrevLSN();

			Leaf resultLeaf = null;
			boolean writeSeparators = false;

			if (right == null) {
				// allocate new right page
				right = allocateLeaf(tx, -1, left.getUnitID(), rootPageID,
						logged);
				PageID rightPageID = right.getPageID();

				byte[] separatorKey = null;

				boolean insertLeft = false;

				if (left.isLast()) {
					// no split necessary
					left.setHighKey(key, logged, -1);
					separatorKey = key.toBytes();
					insertLeft = false;
				} else {
					// actual split
					left.split(right, key, false, true, logged, -1);
					separatorKey = right.getLowKeyBytes();
					insertLeft = true;
				}

				PageID nextPageID = left.getNextPageID();

				if (!bulkContext.firstSplitOccured()) {
					// this is the first split
					bulkContext.setNextPageID(nextPageID);
					bulkContext.setBeforeFirstSplitLSN(rememberedLSN);
				}

				// chain left page with right page
				left.setNextPageID(rightPageID, logged, -1);
				right.setPrevPageID(leftPageID, logged, -1);

				// set right page's next pointer
				right.setNextPageID(nextPageID, logged, -1);

				Leaf newLeftPage = null;
				Leaf newRightPage = null;

				if (insertLeft) {
					newLeftPage = left;
					newRightPage = right;
				} else {
					newLeftPage = right;
					newRightPage = null;
					if (!bulkContext.isLeftmostPage(left)) {
						left.cleanup();
						left = null;
					}
				}

				writeSeparators = bulkContext.splitOccurred(new SeparatorEntry(
						separatorKey, rightPageID), newLeftPage, newRightPage);
				writeSeparators = writeSeparators && !insertLeft;
				resultLeaf = newLeftPage;

			} else {
				// continue bulk insert in right page

				left.setHighKey(key, logged, -1);
				right.moveBeforeFirst();
				byte[] separatorKey = key.toBytes();

				if (!bulkContext.isLeftmostPage(left)) {
					left.cleanup();
					left = null;
				}

				writeSeparators = bulkContext.overflowOccurred(separatorKey);
				resultLeaf = right;

			}

			if (writeSeparators) {
				completeBulkInsert(tx, rootPageID, bulkContext, logged);
			}

			if (log.isTraceEnabled()) {
				log.trace(String.format("Split of non-root page %s completed.",
						left));
				log.trace(left.dump("Left page (splitted)"));
				log.trace(right.dump("Right page (new)"));
			}

			tx.getStatistics().increment(TxStats.BTREE_LEAF_ALLOCATIONS);

			left = null;
			right = null;

			return resultLeaf;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Could not log page split operations.");
		} finally {
			if (left != null) {
				left.cleanup();
			}
			if (right != null) {
				right.cleanup();
			}
		}
	}

	protected void completeBulkInsert(Tx tx, PageID rootPageID,
			BulkInsertContext bulkContext, boolean logged)
			throws IndexAccessException {

		Leaf nextNotModifiedPage = null;

		try {

			if (bulkContext.firstSplitOccured()) {
				// at least one split occurred

				// adjust previous pointer in the next (not modified) leaf
				PageID nextNotModifiedPageID = bulkContext.getNextPageID();
				if (nextNotModifiedPageID != null) {
					nextNotModifiedPage = (Leaf) getPage(tx,
							bulkContext.getNextPageID(), true, false);
					Leaf right = bulkContext.getCurrentRightPage();
					PageID rightMostPageID = (right != null) ? right
							.getPageID() : bulkContext.getCurrentLeftPage()
							.getPageID();
					nextNotModifiedPage.setPrevPageID(rightMostPageID, logged,
							-1);
					nextNotModifiedPage.cleanup();
					nextNotModifiedPage = null;
				}
				// split at this level is complete

				// write dummy CLR to make split at this level invisible to undo
				// processing
				long rememberedLSN = logDummyCLR(tx,
						bulkContext.getBeforeFirstSplitLSN());

				// write separators into parent page(s)
				insertSeparatorsBulk(tx, rootPageID, bulkContext, logged);

				// write dummy CLR to make also split propagation invisible to
				// undo processing
				logDummyCLR(tx, rememberedLSN);

			}

			bulkContext.separatorsWritten();

		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Error while completing the bulk insert!");
		} finally {
			if (nextNotModifiedPage != null) {
				nextNotModifiedPage.cleanup();
			}
		}

	}

	protected Leaf splitRootLeaf(Tx tx, PageID rootPageID, Leaf root,
			XTCdeweyID key, boolean forUpdate, boolean logged)
			throws IndexAccessException {
		long rememberedLSN = tx.checkPrevLSN();
		Leaf left = null;
		Leaf right = null;

		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Begin split of root page %s.",
						rootPageID));
				log.trace(root.dump("Root page"));
			}

			// fetch and latch new left and right page
			left = allocateLeaf(tx, -1, root.getUnitID(), rootPageID, logged);
			right = allocateLeaf(tx, -1, root.getUnitID(), rootPageID, logged);

			boolean insertLeft = false;
			byte[] separatorKey = null;

			if (!forUpdate && root.isLast()) {
				// no split necessary
				insertLeft = false;
				root.setHighKey(key, logged, -1);
				separatorKey = key.toBytes();
			} else {

				// actual split
				insertLeft = root.split(right, key, forUpdate, false, logged,
						-1);
				separatorKey = right.getLowKeyBytes();

			}

			// copy remaining data from root to left page
			root.copyContentAndContextTo(left, logged, -1);
			left.setHighKey(root.getHighKey(), logged, -1);

			// chain left page with right page
			left.setNextPageID(right.getPageID(), logged, -1);
			right.setPrevPageID(left.getPageID(), logged, -1);

			// reformat root page
			Branch rootBranch = (Branch) root.format(false, root.getUnitID(),
					rootPageID, root.getHeight() + 1, BRANCH_COMPRESSION,
					logged, -1);
			rootBranch.setLastInLevel(true);
			// reposition context in converted root page
			rootBranch.moveFirst();

			// insert separator in converted root page
			rootBranch.insert(separatorKey, right.getPageID().getBytes(),
					logged, -1);
			rootBranch.setLowPageID(left.getPageID(), logged, -1);

			// root split is complete
			if (log.isTraceEnabled()) {
				log.trace(String.format("Split of root page %s completed.",
						root));
				log.trace(root.dump("Root page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			// write dummy CLR to make also split propagation invisible to undo
			// processing
			logDummyCLR(tx, rememberedLSN);

			// statistic accounting for root page splits -> index height
			tx.getStatistics().increment(TxStats.BTREE_ROOT_SPLITS);

			// verifySplitPages(left, right, keyType, insertKey, separatorKey,
			// insertPosition, splitPosition, insertIntoLeft,
			// newInsertPosition);

			// Free unneeded split page and return
			if (insertLeft) {
				// unlatch and unfix right split page
				right.cleanup();
				root.cleanup();
				return left;
			} else {
				// unlatch and unfix left split page
				left.cleanup();
				root.cleanup();
				return right;
			}
		} catch (IndexOperationException e) {
			if (left != null) {
				left.cleanup();
			}
			if (right != null) {
				right.cleanup();
			}
			root.cleanup();
			throw new IndexAccessException(e, "Error during root split.");
		}
	}

	public Leaf updateInLeaf(Tx tx, PageID rootPageID, Leaf leaf,
			byte[] newValue, long undoNextLSN) throws IndexAccessException {
		boolean logged = true;

		try {
			if (log.isTraceEnabled()) {
				log.trace(leaf.dump("Page before update"));
			}

			while (!leaf.setValue(newValue, false, logged, undoNextLSN)) {
				// not enough space for update -> split
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Splitting leaf page %s for update of %s at %s",
							leaf, leaf.getKey(), leaf.getOffset()));
				}

				// Split and propagate if necessary.
				if (leaf.getPageID().equals(rootPageID)) {
					leaf = splitRootLeaf(tx, rootPageID, leaf, leaf.getKey(),
							true, logged);
				} else {
					leaf = splitNonRootLeaf(tx, rootPageID, leaf,
							leaf.getKey(), true, logged);
				}

				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Splitting leaf page %s for update of %s at %s",
							leaf, leaf.getKey(), leaf.getOffset()));
				}
			}

			if (log.isTraceEnabled()) {
				log.trace(leaf.dump("Page after update"));
			}

			return leaf;
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e, "Could not log record update.");
		}
	}

	protected Branch splitNonRoot(Tx tx, PageID rootPageID, Branch left,
			byte[] insertKey, byte[] insertValue, boolean logged)
			throws IndexAccessException {
		PageID leftPageID = left.getPageID();
		PageID rightPageID = null;
		Branch right = null;
		Branch target = null;

		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Splitting non-root page %s.",
						leftPageID));
				log.trace(left.dump("Split Page"));
			}

			// Remember LSN of previously logged action.
			long rememberedLSN = tx.checkPrevLSN();

			// find out where to split
			int insertPosition = left.getPosition();
			int splitPosition = chooseSplitPosition(left, insertPosition,
					false);
			left.moveTo(splitPosition - 1);
			byte[] separatorKey = ((insertPosition == splitPosition) && (insertPosition <= left
					.getEntryCount())) ? insertKey : left.getKey(); // also high
																	// key in
																	// left page
			left.moveNext();

			boolean insertIntoLeft = (insertPosition <= splitPosition);
			int newInsertPosition = insertIntoLeft ? insertPosition
					: (((insertPosition == splitPosition) ? 1
							: (insertPosition - splitPosition)));

			// allocate and format new right page
			right = allocateBranch(tx, -1, left.getUnitID(), rootPageID,
					left.getHeight(), left.isCompressed(), logged);
			rightPageID = right.getPageID();

			// promote page pointer to low page of right page
			// and update after page pointer to right page
			right.setLowPageID(left.getValueAsPageID(), logged, -1);
			separatorKey = left.getKey();
			left.setValue(rightPageID.getBytes(), logged, -1);
			left.moveNext();

			// shift remaining second half to right page
			while (!left.isAfterLast()) {
				right.insert(left.getKey(), left.getValue(), logged, -1);
				right.moveNext();
				left.delete(logged, -1);
			}

			// set previous page in right page
			right.setPrevPageID(leftPageID, logged, -1);

			// update previous pointer in next page if it exists
			if ((!left.isLastInLevel()) && (right.getEntryCount() > 0)) {
				right.moveLast();
				Branch next = (Branch) getPage(tx, right.getValueAsPageID(),
						true, false);
				try {
					next.setPrevPageID(right.getPageID(), logged, -1);
				} finally {
					next.cleanup();
				}
			}

			// promote last in level flag
			right.setLastInLevel(left.isLastInLevel());
			left.setLastInLevel(false);

			// split at this level is complete
			if (log.isTraceEnabled()) {
				log.trace(String.format("Split of non-root page %s completed.",
						left));
				log.trace(left.dump("Left page (splitted)"));
				log.trace(right.dump("Right page (new)"));
			}

			// write dummy CLR to make split at this level invisible to undo
			// processing
			rememberedLSN = logDummyCLR(tx, rememberedLSN);

			// verifySplitPages(left, right, keyType, insertKey, separatorKey,
			// insertPosition, splitPosition, insertIntoLeft,
			// newInsertPosition);

			insertSeparator(tx, rootPageID, separatorKey, leftPageID,
					rightPageID, left.getHeight(), logged);

			// write dummy CLR to make also split propagation invisible to undo
			// processing
			logDummyCLR(tx, rememberedLSN);

			tx.getStatistics().increment(TxStats.BTREE_BRANCH_ALLOCATE_COUNT);

			// Free unneeded split page
			if (insertIntoLeft) {
				// unlatch and unfix right split page
				right.cleanup();
				right = null;
				target = left;
				left = null;
				target.moveTo(newInsertPosition);
			} else {
				// unlatch and unfix right left split page and switch to right
				// page
				left.cleanup();
				left = null;
				target = right;
				right = null;
				target.moveTo(newInsertPosition);
			}

			return target;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Could not log page split operations.");
		} finally {
			if (left != null) {
				left.cleanup();
			}
			if (right != null) {
				right.cleanup();
			}
		}
	}

	protected Branch descendToParent(Tx tx, PageID rootPageID, PageID pageID,
			byte[] separatorKey, PageID targetPageID, int targetHeight)
			throws IndexAccessException {
		Branch parentPage = null;
		Branch page = (Branch) descend(tx, rootPageID,
				SearchMode.GREATER_OR_EQUAL, separatorKey, targetHeight, true);

		// We are now already at the correct level.
		// Move right until we find the separator we are looking for.
		try {
			while (true) {
				// try to locate separator in this page
				if (page.search(SearchMode.GREATER_OR_EQUAL, separatorKey, null) <= 0) {
					// separator is greater or equal: inspect previous pointer
					PageID p;
					if (page.getPosition() > 1) {
						page.hasPrevious();
						p = page.getValueAsPageID();
						page.moveNext();
					} else {
						p = page.getLowPageID();
					}
					if (p.equals(targetPageID)) {
						parentPage = page;
					}
				} else if (!page.isAfterLast()) {
					// check if we find the separator after the current position
					// in this page, i.e.,
					// we look for the last pointer in this page or there may be
					// duplicate separator keys
					do {
						if (page.getValueAsPageID().equals(targetPageID)) {
							page.moveNext();
							parentPage = page;
							break;
						}
					} while ((page.hasNext())
							&& (Field.DEWEYID.compare(separatorKey,
									page.getKey()) <= 0));
				}

				if (parentPage != null) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Found parent page %s of %s.",
								page.getPageID(), targetPageID));
					}

					return page;
				}

				// follow "next" pointer of this page while
				// we hold the latch on this page
				pageID = page.getValueAsPageID();
				Branch next = (Branch) getPage(tx, pageID, true, false);
				page.cleanup();
				page = next;
			}
		} catch (IndexOperationException e) {
			if (page != null) {
				page.cleanup();
			}
			throw new IndexAccessException(e,
					"An error occured while accessing an index page.");
		}
	}

	private void insertSeparator(Tx tx, PageID rootPageID, byte[] separatorKey,
			PageID leftPageID, PageID rightPageID, int height,
			boolean logged) throws IndexAccessException {
		// find insert position for separator entry in parent page
		// we may keep the target pages latched exclusively because traversals
		// do not perform latch coupling
		Branch parent = descendToParent(tx, rootPageID, rootPageID,
				separatorKey, leftPageID, height + 1);

		if (log.isTraceEnabled()) {
			log.trace(String.format(
					"Insert separator (%s, %s) in parent page %s.",
					Field.DEWEYID.toString(separatorKey), rightPageID, parent));
		}

		parent = insertIntoBranch(tx, rootPageID, parent, separatorKey,
				rightPageID.getBytes(), logged, -1);
		parent.cleanup();
	}

	private void insertSeparatorsBulk(Tx tx, PageID rootPageID,
			BulkInsertContext bulkContext, boolean logged)
			throws IndexAccessException {
		byte[] firstSeparatorKey = bulkContext.getFirstSeparatorKey();

		// find insert position for separator entry in parent page
		Branch parent = descendToParent(tx, rootPageID, rootPageID,
				firstSeparatorKey, bulkContext.getLeftmostPageID(), 1);

		if (log.isTraceEnabled()) {
			log.trace(String.format("Insert separators in parent page %s.",
					parent));
		}

		try {

			// insert separators
			SeparatorEntry[] separators = bulkContext.getSeparators();
			int length = bulkContext.getNumberOfSeparators();

			for (int i = 0; i < length; i++) {
				SeparatorEntry entry = separators[i];
				parent = insertIntoBranch(tx, rootPageID, parent,
						entry.deweyID, entry.pageID.getBytes(), logged,
						-1);
				parent.moveNext();
			}

		} catch (IndexOperationException e) {
			throw new IndexAccessException(e);
		} finally {
			parent.cleanup();
		}
	}

	protected int chooseSplitPosition(Branch splitPage, int insertPosition,
			boolean compact) throws IndexOperationException {
		int entryCount = splitPage.getEntryCount();
		byte[] separatorKey = null;

		if (entryCount < 3) {
			log.error(String
					.format("Cannot split page %s because it contains less than three records.",
							splitPage.getPageID()));
			log.error(splitPage.dump("Page to split"));
			throw new IndexOperationException(
					"Cannot split page %s because it contains less than three records.",
					splitPage.getPageID());
		}

		if (compact) {
			if (insertPosition == entryCount + 1) {
				return entryCount;
			} else {
				return entryCount - 1;
			}
		} else {
			return (entryCount / 2) + 1;
		}
	}

	protected Branch splitRoot(Tx tx, PageID rootPageID, Branch root,
			byte[] insertKey, byte[] insertValue,
			boolean logged) throws IndexAccessException {
		long rememberedLSN = tx.checkPrevLSN();
		Branch left = null;
		Branch right = null;

		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Begin split of root page %s.",
						rootPageID));
				log.trace(root.dump("Root page"));
			}

			// fetch and latch new left and right page
			left = allocateBranch(tx, -1, root.getUnitID(), rootPageID,
					root.getHeight(), root.isCompressed(), logged);
			right = allocateBranch(tx, -1, root.getUnitID(), rootPageID,
					root.getHeight(), root.isCompressed(), logged);

			// find out where to split
			int insertPosition = root.getPosition();
			int splitPosition = chooseSplitPosition(root, insertPosition,
					false);
			root.moveTo(splitPosition - 1);
			byte[] separatorKey = ((insertPosition == splitPosition) && (insertPosition <= root
					.getEntryCount())) ? insertKey : root.getKey();
			root.moveNext();

			boolean insertIntoLeft = insertPosition <= splitPosition;
			int newInsertPosition = insertIntoLeft ? insertPosition
					: ((insertPosition == splitPosition) ? 1
							: (insertPosition - splitPosition));

			// promote page pointer to low page of right page and drop it
			right.setLowPageID(root.getValueAsPageID(), logged, -1);
			separatorKey = root.getKey();
			root.delete(logged, -1);

			// copy low page ID to left page
			left.setLowPageID(root.getLowPageID(), logged, -1);

			// shift second half to right page
			while (!root.isAfterLast()) {
				right.insert(root.getKey(), root.getValue(), logged, -1);
				right.moveNext();
				root.delete(logged, -1);
			}

			// shift first half to left page
			left.moveFirst();
			root.moveFirst();
			while (!root.isAfterLast()) {
				left.insert(root.getKey(), root.getValue(), logged, -1);
				left.moveNext();
				root.delete(logged, -1);
			}

			// set previous page in right page
			right.setPrevPageID(left.getPageID(), logged, -1);

			// reformat root page
			root.format(root.getUnitID(), rootPageID, root.getHeight() + 1,
					root.isCompressed(), logged, -1);
			root.setLastInLevel(true);
			// reposition context in converted root page
			root.moveFirst();

			// add right link from left page to right page
			left.insert(separatorKey, right.getPageID().getBytes(), logged, -1);

			// mark right page as last in this level
			right.setLastInLevel(true);

			// insert separator in converted root page
			root.insert(separatorKey, right.getPageID().getBytes(), logged, -1);
			root.setLowPageID(left.getPageID(), logged, -1);

			// root split is complete
			if (log.isTraceEnabled()) {
				log.trace(String.format("Split of root page %s completed.",
						root));
				log.trace(root.dump("Root page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			// write dummy CLR to make also split propagation invisible to undo
			// processing
			logDummyCLR(tx, rememberedLSN);

			// statistic accounting for root page splits -> index height
			tx.getStatistics().increment(TxStats.BTREE_ROOT_SPLITS);

			// verifySplitPages(left, right, keyType, insertKey, separatorKey,
			// insertPosition, splitPosition, insertIntoLeft,
			// newInsertPosition);

			// Free unneeded split page and return
			if (insertIntoLeft) {
				// unlatch and unfix right split page
				left.moveTo(newInsertPosition);
				right.cleanup();
				root.cleanup();
				return left;
			} else {
				// unlatch and unfix left split page
				right.moveTo(newInsertPosition);
				left.cleanup();
				root.cleanup();
				return right;
			}
		} catch (IndexOperationException e) {
			if (left != null) {
				left.cleanup();
			}
			if (right != null) {
				right.cleanup();
			}
			root.cleanup();
			throw new IndexAccessException(e, "Error during root split.");
		}
	}

	private Branch collapseRoot(Tx tx, Branch root, BPContext left,
			BPContext right, boolean logged) throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Starting collapse of root page %s.",
						root));
				log.trace(root.dump("Root page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			Leaf rootLeaf = null;

			if (!left.isLeaf()) {
				Branch leftBranch = (Branch) left;
				Branch rightBranch = (Branch) right;

				// switch root page type and update pointers
				root.setLowPageID(null, logged, -1);
				root.format(leftBranch.getUnitID(), root.getPageID(),
						leftBranch.getHeight(), leftBranch.isCompressed(),
						logged, -1);
				root.setLastInLevel(true);
				root.moveFirst();

				// copy before page of right page to root
				root.setLowPageID(rightBranch.getLowPageID(), logged, -1);

				// move content of right page to root
				right.moveFirst();
				while (!rightBranch.isAfterLast()) {
					root.moveNext();
					root.insert(rightBranch.getKey(), right.getValue(), logged,
							-1);
					rightBranch.delete(logged, 1);
				}

				// "Reset" page properties to get the required undo information
				// in the log
				rightBranch.setPrevPageID(null, logged, -1);
				leftBranch.setPrevPageID(null, logged, -1);
				rightBranch.setLowPageID(null, logged, -1);
				leftBranch.setLowPageID(null, logged, -1);
				leftBranch.format(leftBranch.getUnitID(),
						leftBranch.getRootPageID(), leftBranch.getHeight(),
						leftBranch.isCompressed(), logged, -1);
				rightBranch.format(right.getUnitID(), left.getRootPageID(),
						right.getHeight(), leftBranch.isCompressed(), logged,
						-1);
			} else {
				throw new RuntimeException("Root collapse at height 1!");

				// switch root page type and update pointers
				// root.setLowPageID(null, logged, -1);
				// rootLeaf = (Leaf) root.format(true, left.getUnitID(),
				// root.getPageID(), left.getHeight(), true, logged, -1);

				// // move content of right page to root
				// right.moveFirst();
				// while (!right.isAfterLast())
				// {
				// root.moveNext();
				// root.insert(right.getKey(), right.getValue(), true, logged,
				// -1);
				// right.delete(true, logged, 1);
				// }
				//
				// // left and right page are empty leaf pages
				// // "Reset" page properties to get the required undo
				// information in the log
				// right.setPrevPageID(null, logged, -1);
				// left.setPrevPageID(null, logged, -1);
				// left.format(left.getUnitID(), PageType.LEAF,
				// left.getRootPageID(), left.getKeyType(), left.getValueType(),
				// left.getHeight(), left.isUnique(), left.isCompressed(),
				// logged, -1);
				// right.format(right.getUnitID(), PageType.LEAF,
				// left.getRootPageID(), right.getKeyType(),
				// right.getValueType(), right.getHeight(), right.isUnique(),
				// right.isCompressed(), logged, -1);
			}

			if (log.isTraceEnabled()) {
				log.trace(String.format("Finished collapse of root page %s.",
						root));
				log.trace(root.dump("Root page"));
			}

			// delete pages
			left.deletePage();
			left = null;
			right.deletePage();
			right = null;

			// left page became empty after deletion: "next higher" key is now
			// first in root page
			root.moveFirst();
			return root;
		} catch (IndexOperationException e) {
			if (right != null) {
				right.cleanup();
			}
			if (left != null) {
				left.cleanup();
			}
			root.cleanup();
			throw new IndexAccessException(e,
					"Could not log root collapse operations.");
		}
	}

	public Leaf readFromLeaf(Tx tx, PageID rootPageID, Leaf leaf, byte[] key,
			byte[] value) throws IndexAccessException {
		// TODO
		try {
			readLockEntry(tx, rootPageID, leaf, key, value);
			return leaf;
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e);
		}
	}

	public void dumpLeafs(Tx tx, PageID rootPageID, PrintStream out)
			throws IndexAccessException {

		out.println(String.format("Leaf pages for root page ID %s:\n",
				rootPageID));

		Leaf currentLeaf = descend(tx, rootPageID, SearchMode.FIRST, null,
				false);
		Leaf nextLeaf = null;
		PageID nextLeafPageID = null;

		try {
			while (true) {
				out.println(currentLeaf.dump(null));

				nextLeafPageID = currentLeaf.getNextPageID();
				if (nextLeafPageID != null) {
					nextLeaf = (Leaf) getPage(tx, nextLeafPageID, false, false);
					currentLeaf.cleanup();
					currentLeaf = nextLeaf;
				} else {
					currentLeaf.cleanup();
					break;
				}
			}
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e);
		}
	}

	private PageID determineNextPageID(Tx tx, Branch parent)
			throws IndexOperationException {
		if (parent.getPosition() == 1) {
			return parent.getLowPageID();
		} else {
			return parent.getValueAsPageID();
		}
	}

	protected Branch deleteFromBranch(Tx tx, PageID rootPageID, Branch page,
			byte[] deleteKey, boolean logged, long undoNextLSN)
			throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Deleting (%s, %s) from page %s.",
						Field.DEWEYID.toString(deleteKey),
						Field.PAGEID.toString(page.getValue()),
						page.getPageID()));
			}

			page.delete(logged, undoNextLSN);

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after delete"));
			}

			int deleteType = TxStats.BTREE_BRANCH_DELETES;
			tx.getStatistics().increment(deleteType);

			if ((page.isLastInLevel()) || (page.getEntryCount() > 0)) {
				// never propagate that last page in level underflows
				return page;
			}

			// branch page is empty and its next page pointer was converted to
			// low page
			byte[] highKey = deleteKey;
			return handleUnderflow(tx, rootPageID, page, highKey, logged);
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e,
					"Could not perform record deletion.");
		}
	}

	protected Branch handleUnderflow(Tx tx, PageID rootPageID, Branch page,
			byte[] highKey, boolean logged) throws IndexAccessException {
		long rememberedLSN = tx.checkPrevLSN();
		Branch parent = null;
		Branch next = null;

		try {
			page.moveLast();
			next = unchain(tx, page, logged);
			// write CLR to skip tree reorganization during undo
			logDummyCLR(tx, rememberedLSN);

			try {
				// remove separator from parent (may propagate up the tree)
				// note, we may keep the page X-latched because no split from
				// lower levels can block
				parent = deleteSeparator(tx, rootPageID, page.getPageID(),
						page.getHeight(), highKey, logged);
			} catch (IndexAccessException e) {
				page.cleanup();
				throw e;
			}

			// reduce index height when parent is emptied root
			if ((parent.getEntryCount() == 0)
					&& (parent.getPageID().equals(rootPageID))) {
				// This situation implies the following:
				// a) parent is empty but was not cleaned up: it is the last
				// page in it's level
				// b) parent is empty: the right sibling is the last page in
				// this level is drained empty
				// c) page is not the last page in this level: it is the left
				// sibling

				// collapse root if right page is not splitted concurrently
				// separator is missing
				if (next.isLastInLevel()) {
					return collapseRoot(tx, parent, page, next, logged);
				}
			}
			parent.cleanup();

			// get the next page we should continue with
			while ((!next.isLastInLevel()) && (next.getEntryCount() < 2)) {
				next.moveLast();
				Branch tmp = (Branch) getPage(tx, next.getValueAsPageID(),
						true, false);
				next.cleanup();
				next = tmp;
			}

			// "Reset" page properties to get the required undo information in
			// the log
			page.setPrevPageID(null, logged, -1);
			page.setLowPageID(null, logged, -1);
			page.format(page.getUnitID(), rootPageID, page.getHeight(),
					page.isCompressed(), logged, -1);
			page.deletePage();
			page = null;

			// write CLR to skip tree reorganization during undo
			logDummyCLR(tx, rememberedLSN);

			return next;
		} catch (IndexOperationException e) {
			if (parent != null) {
				parent.cleanup();
			}
			if (next != null) {
				next.cleanup();
			}
			if (page != null) {
				page.cleanup();
			}
			throw new IndexAccessException(e);
		}
	}

	private Branch deleteSeparator(Tx tx, PageID rootPageID, PageID pageID,
			int height, byte[] highKey, boolean logged)
			throws IndexAccessException {
		Branch parent = null;
		try {
			parent = descendToParent(tx, rootPageID, rootPageID, highKey,
					pageID, height + 1);

			// parent context is positioned one record after the separator
			if (parent.hasPrevious()) {
				// simply delete separator to page from parent
				if (log.isTraceEnabled()) {
					log.trace(String
							.format("Deleting separator to leaf page %s from parent %s",
									pageID, parent));
				}
				parent = deleteFromBranch(tx, rootPageID, parent,
						parent.getKey(), logged, -1);
			} else {
				// make next page new before page in parent
				if (log.isTraceEnabled()) {
					log.trace(String
							.format("Deleting separator to new before leaf page %s (current value = %s) in parent %s.",
									pageID, parent.getValueAsPageID(), parent));
				}

				parent.setLowPageID(parent.getValueAsPageID(), logged, -1);
				parent = deleteFromBranch(tx, rootPageID, parent,
						parent.getKey(), logged, -1);
			}
			return parent;
		} catch (IndexOperationException e) {
			parent.cleanup();
			throw new IndexAccessException(e);
		}
	}

	private void deleteLeafReferences(Tx tx, PageID rootPageID,
			PageID leftBorder, PageID rightBorder, byte[] highKey,
			boolean logged) throws IndexAccessException {
		Branch parent = descendToParent(tx, rootPageID, rootPageID, highKey,
				leftBorder, 1);
		Branch next = null;

		try {
			boolean firstPage = true;

			while (true) {

				PageID currentPageID = parent.getValueAsPageID();

				if (firstPage) {

					if (currentPageID.equals(rightBorder)) {
						// finished
						break;
					}

					if (parent.isLast()) {
						// move to next page
						next = (Branch) getPage(tx, currentPageID, true, false);
						parent.cleanup();
						parent = next;
						next = null;
						firstPage = false;
					} else {
						if (log.isTraceEnabled()) {
							log.trace(String.format(
									"Deleting PageID %s from Parent %s.",
									currentPageID, parent.getPageID()));
						}
						parent = deleteFromBranch(tx, rootPageID, parent,
								parent.getKey(), logged, -1);
					}

				} else {

					if (parent.getLowPageID().equals(rightBorder)) {
						// finished
						break;
					}

					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Deleting PageID %s from Parent %s.",
								parent.getLowPageID(), parent.getPageID()));
					}
					parent.setLowPageID(currentPageID, logged, -1);
					parent = deleteFromBranch(tx, rootPageID, parent,
							parent.getKey(), logged, -1);
				}
			}

			parent.cleanup();
		} catch (IndexOperationException e) {
			parent.cleanup();
			if (next != null) {
				try {
					next.cleanup();
				} catch (Exception ex) {
				}
			}
			throw new IndexAccessException(e);
		}
	}

	private Branch unchain(Tx tx, Branch page, boolean logged)
			throws IndexAccessException {
		Branch previous = null;
		Branch next = null;
		PageID previousPageID;
		int retry = 0;

		// unchain leaf
		try {
			while ((previousPageID = page.getPrevPageID()) != null) {
				// unlatch is save here because page is empty
				// and others will not modify its content
				page.unlatch();
				try {
					previous = (Branch) getPage(tx, previousPageID, true, false);
				} catch (IndexOperationException e) {
					page.latchX();
					if (++retry == 1000) {
						// avoid starvation of this thread
						throw new IndexOperationException(
								e,
								"Failed %s times to grab previous page for unchain. Aborting to avoid starvation",
								retry);
					}
					continue;
				}

				page.latchX();
				previous.moveLast();
				PageID checkPrevPageID = page.getPrevPageID();

				if (checkPrevPageID == null) {
					previous.cleanup();
					previous = null;
					break;
				}
				if (checkPrevPageID.equals(previousPageID)) {
					break;
				}
			}

			PageID nextPageID = page.getLowPageID();
			next = (Branch) getPage(tx, nextPageID, true, false);
			next.setPrevPageID(previousPageID, logged, -1);

			if (previous != null) {
				previous.setValue(nextPageID.getBytes(), logged, -1);
				previous.cleanup();
				previous = null;
			}

			return next;
		} catch (IndexOperationException e) {
			if (next != null) {
				next.cleanup();
			}
			if (previous != null) {
				previous.cleanup();
			}
			page.cleanup();
			throw new IndexAccessException(e);
		}
	}

	public String printLeafScannerStats(NavigationMode navMode) {
		return !COLLECT_STATS ? "No statistics available!" : scannerMap.get(
				navMode).printStats();
	}

	public void deleteFromLeaf(Tx tx, PageID rootPageID, Leaf left,
			SubtreeDeleteListener deleteListener, long undoNextLSN,
			boolean logged) throws IndexAccessException {
		Leaf right = null;
		Leaf temp = null;
		Branch parent = null;

		try {

			List<PageID> externalPageIDs = new ArrayList<PageID>();
			XTCdeweyID subtreeRoot = left.getKey();

			// find "correct" left page (that contains the node PREVIOUS to the
			// subtree root)
			// delete subtree (beginning) from left page
			while (true) { // while an EmptyLeafException occurs (-> max. one
							// time)
				try {
					// try to delete subtree locally
					if (left.deleteSubtreeStart(deleteListener,
							externalPageIDs, false, logged, undoNextLSN)) {
						// deletion successful within current leaf
						deleteExternalized(tx, externalPageIDs);
						return;
					} else {
						// load next page

						XTCdeweyID highKey = left.getHighKey();

						if (highKey == null || !subtreeRoot.isPrefixOf(highKey)) {
							// subtree is not continued in next page
							deleteExternalized(tx, externalPageIDs);
							deleteListener.subtreeEnd();
							return;
						}

						// subtree is (probably) continued in next page
						right = (Leaf) getPage(tx, left.getNextPageID(), true,
								false);

						break;
					}
				} catch (EmptyLeafException ex) {
					// current leaf needs to be unchained

					if (left.getNextPageID() == null) {
						// left is last page and becomes empty
						left.deleteSubtreeEnd(subtreeRoot, deleteListener,
								null, false, logged, undoNextLSN);
						return;
					}

					// find correct left page
					PageID previousPageID = left.getPrevPageID();
					left.cleanup();
					temp = left;
					left = (Leaf) getPage(tx, previousPageID, true, false);
					left.assignDeweyIDBuffer(temp);
					temp = null;
					XTCdeweyID highKey = null;

					while (true) {
						temp = (Leaf) getPage(tx, left.getNextPageID(), true,
								false);
						highKey = temp.getHighKey();
						if (subtreeRoot.compareDivisions(highKey) >= 0) {
							left.cleanup();
							temp.assignDeweyIDBuffer(left);
							left = temp;
							temp = null;
						} else {
							// subtree root is located in leaf
							if (subtreeRoot.compareDivisions(temp.getLowKey()) == 0) {
								right = temp;
								temp = null;
							} else {
								left.cleanup();
								temp.assignDeweyIDBuffer(left);
								left = temp;
								temp = null;
							}
							break;
						}
					}

					if (right != null) {
						// found next page to inspect
						break;
					}
				}
			}

			// begin of subtree deleted from left page -> inspect current right
			// page

			List<PageID> innerPageIDs = new ArrayList<PageID>();
			XTCdeweyID newHighKey = null;
			// inspect leaf pages until subtree end is found
			while (!right.deleteSubtreeEnd(subtreeRoot, deleteListener,
					externalPageIDs, false, logged, undoNextLSN)) {

				PageID nextPageID = right.getNextPageID();
				if (nextPageID == null) {
					// right page is last leaf -> right page becomes empty, but
					// is not unchained

					// notify listeners about subtree end
					deleteListener.subtreeEnd();
					break;
				}

				innerPageIDs.add(right.getPageID());
				newHighKey = right.getHighKey();
				temp = (Leaf) getPage(tx, nextPageID, true, false);
				right.cleanup();
				temp.assignDeweyIDBuffer(right);
				right = temp;
				temp = null;
			}

			PageID leftPageID = left.getPageID();
			PageID rightPageID = right.getPageID();

			// postcommit hook for blob values
			deleteExternalized(tx, externalPageIDs);

			if (innerPageIDs.isEmpty()) {
				// deletion finished
				right.cleanup();
				right = null;
				return;
			}

			// new chaining between left and right page
			left.setNextPageID(rightPageID, logged, undoNextLSN);
			right.setPrevPageID(leftPageID, logged, undoNextLSN);
			byte[] oldHighKey = left.getHighKeyBytes();
			left.setHighKey(newHighKey, logged, undoNextLSN);

			// postcommit hook for deleting the inner pages
			tx.addPostCommitHook(new DeletePageHook(rootPageID, innerPageIDs));

			// delete all page pointers between the left border and right border
			// page
			deleteLeafReferences(tx, rootPageID, leftPageID, rightPageID,
					oldHighKey, logged);

			right.cleanup();
			right = null;
			left.cleanup();
			left = null;

		} catch (IndexOperationException e) {
			try {
				if (left != null) {
					left.cleanup();
				}
			} catch (Exception ex) {
			}
			try {
				if (right != null) {
					right.cleanup();
				}
			} catch (Exception ex) {
			}
			try {
				if (temp != null) {
					temp.cleanup();
				}
			} catch (Exception ex) {
			}
			try {
				if (parent != null) {
					parent.cleanup();
				}
			} catch (Exception ex) {
			}
			throw new IndexAccessException(e, "Error deleting from index %s.",
					rootPageID);
		}
	}

	private void deleteExternalized(Tx tx, List<PageID> externalPageIDs) {
		if (!externalPageIDs.isEmpty()) {
			tx.addPostCommitHook(new DeleteExternalizedHook(blobStore,
					externalPageIDs));
		}
	}

	/**
	 * Moves all nodes from the left page (which are located after the context)
	 * to the beginning of the right page. The left leaf's cursor will not
	 * change, but in the end the right cursor will point to the last moved
	 * node.
	 * 
	 * @param left
	 * @param right
	 * @param logged
	 * @throws IndexAccessException
	 */
	private void moveNodes(Leaf left, Leaf right, boolean logged)
			throws IndexAccessException {

		try {

			// delete nodes from left page
			BracketNodeSequence nodesToMove = left.deleteSequenceAfter(logged,
					-1);

			// move right leaf's cursor to the beginning
			right.moveBeforeFirst();

			// insert nodes into right page
			if (!right.insertSequenceAfter(nodesToMove, logged, -1)) {
				// not enough space in right page
				throw new IndexOperationException(
						"Right leaf does not have enough space for moving the nodes.");
			}

		} catch (IndexOperationException e) {
			try {
				if (left != null) {
					left.cleanup();
				}
			} catch (Exception ex) {
			}
			try {
				if (right != null) {
					right.cleanup();
				}
			} catch (Exception ex) {
			}
			throw new IndexAccessException(e,
					"Error moving nodes between leaf pages.");
		}
	}

}
