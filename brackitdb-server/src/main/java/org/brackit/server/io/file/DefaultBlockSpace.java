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
package org.brackit.server.io.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.procedure.InfoContributor;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.server.procedure.statistics.ListContainers;
import org.brackit.server.util.BitArrayWrapper;
import org.brackit.server.util.BitMap;
import org.brackit.server.util.Calc;
import org.brackit.server.util.FileUtil;

/**
 * Default BlockSpace implementation using one BlockFile, simple 1:1 mapping
 * from LBA to block numbers in the file.
 * 
 * This is used to replace the old XTCioMgr01 implementation. However, the
 * allocated/released info is not stored in the block header, but only in the
 * BitArray for better performance. The BitArray can be serialized in a log-like
 * style to achieve the same effect as that of the old implementation.
 * 
 * @author Ou Yi
 * @author Sebastian Baechle
 * 
 */
public class DefaultBlockSpace implements BlockSpace, InfoContributor {

	private static final Logger log = Logger.getLogger(DefaultBlockSpace.class);

	static final byte BLOCK_IN_USE = (byte) 0xFF;
	static final int BLOCK_HEADER_LENGTH = 1;

	final int id;
	int blkSize;

	String storeRoot;
	String dataFileName;
	String metaFileName;
	String unitFileNamePrefix;
	String unitFileNameFormat;
	BlockFile dataFile;
	RandomAccessFile metaFile;
	int iniSize;
	int extSize;
	BitMap freeSpaceInfo;

	Map<Integer, Unit> unitMap;
	int nextUnit = 1;

	int nextFreeBlockHint;
	boolean closed = true;
	public byte[] iniBlock;
	private int freeBlockCount;
	private int usedBlockCount;

	/**
	 * Creates the object (in memory) representing the BlockSpace.
	 * 
	 * @param id
	 */
	public DefaultBlockSpace(String root, int id) {
		this.id = id;

		storeRoot = root;
		dataFileName = storeRoot + File.separator + id + ".cnt";
		metaFileName = dataFileName + ".meta";
		unitFileNamePrefix = dataFileName + ".unit";
		unitFileNameFormat = unitFileNamePrefix + "%03d";
		ProcedureUtil.register(ListContainers.class, this);
	}

	@Override
	public boolean isUsed(int lba) {
		synchronized (freeSpaceInfo) {
			return lba >= 0 && lba < freeSpaceInfo.logicalSize()
					&& freeSpaceInfo.get(lba);
		}
	}

	@Override
	public int allocate(int lba, int unitID) throws StoreException {
		freeBlockCount--;
		usedBlockCount++;
		return allocateImpl(lba, unitID);
	}

	private synchronized int allocateImpl(int lba, int unitID)
			throws StoreException {

		Unit unit = unitMap.get(unitID);
		if (unit == null) {
			throw new StoreException(String.format("UnitID %s does not exist!",
					unitID));
		}

		// lba >= 0, the caller decides which block to allocate
		if (lba >= 0) {
			while (lba >= freeSpaceInfo.logicalSize()) {
				extendStore();
			}
			if (freeSpaceInfo.get(lba)) {
				throw new StoreException("block already allocated, lba: " + lba);
			}
			nextFreeBlockHint = lba + 1;
			freeSpaceInfo.set(lba);
			unit.blockTable.set(lba);
			return lba; // Note we use one to one mapping for lba >> blockNo
		}

		// lba < 0, we decide which block to allocate

		int blockNo = -1;
		// first try to find a block without extending the space
		if (nextFreeBlockHint < freeSpaceInfo.logicalSize()) {
			blockNo = freeSpaceInfo.nextClearBit(nextFreeBlockHint);
			if (blockNo >= 0) {
				nextFreeBlockHint = blockNo + 1;
				freeSpaceInfo.set(blockNo);
				unit.blockTable.set(blockNo);
				return blockNo; // Note we use one to one mapping for lba >>
				// blockNo
			}
		}

		// there is no free block in the existing space
		nextFreeBlockHint = freeSpaceInfo.logicalSize();
		extendStore();
		blockNo = freeSpaceInfo.nextClearBit(nextFreeBlockHint);

		nextFreeBlockHint = blockNo + 1;
		freeSpaceInfo.set(blockNo);
		unit.blockTable.set(blockNo);
		return blockNo; // Note we use one to one mapping for lba >> blockNo
	}

	private void extendStore() throws StoreException {
		extendStoreImpl();
	}

	private synchronized void extendStoreImpl() throws StoreException {

		int newStoreSize = freeSpaceInfo.logicalSize() + extSize;
		log.debug(String.format(
				"extending block space, old size: %s, new size %s",
				freeSpaceInfo.logicalSize(), newStoreSize));

		try {

			dataFile.close();

			// no autoSync to speed up the initialization
			dataFile.open(false);

			for (int i = freeSpaceInfo.logicalSize(); i < newStoreSize; i++) {
				dataFile.write(i, iniBlock, 1);
			}

			dataFile.close();

			freeSpaceInfo.extendTo(newStoreSize);
			freeBlockCount += extSize;

			for (Unit unit : unitMap.values()) {
				unit.blockTable.extendTo(newStoreSize);
			}

			syncMeta(false, false);

			dataFile.open(true);

		} catch (FileException e) {
			throw new StoreException(e);
		}
	}

	@Override
	public int sizeOfHeader() {
		return BLOCK_HEADER_LENGTH;
	}

	@Override
	public void release(int lba, int hintUnitID) throws StoreException {
		releaseImpl(lba, hintUnitID);
		usedBlockCount--;
		freeBlockCount++;
	}

	private synchronized void releaseImpl(int lba, int hintUnitID)
			throws StoreException {

		// mark block as free
		freeBlock(lba);

		// release block from unit
		if (hintUnitID > 0) {
			Unit unit = unitMap.get(hintUnitID);
			if (unit != null && unit.blockTable.get(lba)) {
				// hintUnitID was right
				unit.blockTable.clear(lba);
				return;
			}
		}

		for (Unit unit : unitMap.values()) {
			unit.blockTable.clear(lba);
		}
	}

	private void freeBlock(int lba) throws StoreException {

		if (!freeSpaceInfo.get(lba)) {
			throw new StoreException("invalid lba, block not in use");
		}
		freeSpaceInfo.clear(lba);
		if (nextFreeBlockHint > lba) {
			nextFreeBlockHint = lba;
		}
	}

	@Override
	public synchronized void close() throws StoreException {
		log.info("closing block space: " + id);
		ProcedureUtil.deregister(ListContainers.class, this);

		if (closed) {
			throw new StoreException("invalid state, space already closed");
		}
		try {
			dataFile.close();

			syncMeta(false, true);

			for (Unit unit : unitMap.values()) {
				unit.close();
			}

			metaFile.close();

			closed = true;
		} catch (FileException e) {
			throw new StoreException(e);
		} catch (IOException e) {
			throw new StoreException(e);
		}
		log.info("block space " + id + " closed");
	}

	private void syncMeta(boolean writeHeader, boolean markConsistent)
			throws StoreException {
		// write the up-to-date length
		try {

			if (writeHeader) {
				metaFile.seek(0);
				metaFile.writeInt(blkSize);
				metaFile.writeInt(iniSize);
				metaFile.writeInt(extSize);
			} else {
				metaFile.seek(12);
			}

			// mark metadata as inconsistent before proceeding
			metaFile.writeInt(1);
			metaFile.getFD().sync();

			// so that we know how many bytes to read for the free space
			// administration
			metaFile.writeInt(freeSpaceInfo.toBytes().length);
			metaFile.write(freeSpaceInfo.toBytes());

			metaFile.writeInt(freeBlockCount);
			metaFile.writeInt(usedBlockCount);

			metaFile.getFD().sync();

			// sync unit mappings
			for (Unit unit : unitMap.values()) {
				unit.sync();
			}

			if (markConsistent) {
				// everything is OK. mark consistent and flush
				metaFile.seek(12);
				metaFile.writeInt(0);
				metaFile.getFD().sync();
			}

		} catch (IOException e) {
			throw new StoreException("failure syncing meta file");
		}
	}

	@Override
	public void create(int blkSize, int iniSize, double extent)
			throws StoreException {

		File storeRootDir = new File(storeRoot);

		if (log.isTraceEnabled()) {
			log.trace("storage root directory: "
					+ storeRootDir.getAbsolutePath());
		}
		if (storeRootDir.exists()) {
			if (log.isInfoEnabled()) {
				log.warn("storage root directory exists, deleting...");
			}
			FileUtil.forceRemove(storeRootDir);
		}
		storeRootDir.mkdirs();

		if (extent > 1) {
			log.warn("ext ratio larger than 1.0");
		}
		this.blkSize = blkSize;
		this.iniSize = iniSize;
		this.extSize = (int) (iniSize * extent);

		this.usedBlockCount = 0;
		this.freeBlockCount = iniSize;

		// note byte 0 of the INIT_BLOCK is 0, indicating a block not in use,
		// see create()
		iniBlock = new byte[blkSize];
		for (int i = 0; i < iniBlock.length; i++) {
			iniBlock[i] = (byte) 0;
		}
		try {
			File data = new File(dataFileName);
			if (data.exists()) {
				data.delete();
			}
			File meta = new File(metaFileName);
			if (meta.exists()) {
				meta.delete();
			}
			deleteUnitFiles();

			dataFile = new RAFBlockFile(dataFileName, blkSize);

			// no autoSync to speed up the initialization
			dataFile.open(false);

			for (int i = 0; i < iniSize; i++) {
				dataFile.write(i, iniBlock, 1);
			}

			dataFile.close();

			// no need to clear the bits individually, they are all clear by
			// default
			freeSpaceInfo = new BitArrayWrapper(iniSize);

			// the first block reserved, for compatibility with the code which
			// depends on the old IOMgr
			freeSpaceInfo.set(0);

			// start with empty unit mapping
			unitMap = new HashMap<Integer, Unit>();
			nextUnit = 1;

			metaFile = new RandomAccessFile(metaFileName,
					Constants.FILE_MODE_UNSY);

			syncMeta(true, true);

			metaFile.close();

		} catch (FileException e) {
			throw new StoreException(e);
		} catch (IOException e) {
			throw new StoreException(e);
		}
	}

	@Override
	public synchronized void open() throws StoreException {
		log.info("opening block space: " + id);
		if (!closed) {
			throw new StoreException("invalid state, space already opened");
		}
		try {

			metaFile = new RandomAccessFile(metaFileName,
					Constants.FILE_MODE_UNSY);

			blkSize = metaFile.readInt();
			iniSize = metaFile.readInt();
			extSize = metaFile.readInt();

			boolean consistent = (metaFile.readInt() == 0);

			iniBlock = new byte[blkSize];
			for (int i = 0; i < iniBlock.length; i++) {
				iniBlock[i] = (byte) 0;
			}
			dataFile = new RAFBlockFile(dataFileName, blkSize);
			dataFile.open(true);

			if (consistent) {
				int freeSpaceInfoBytes = metaFile.readInt();
				byte[] b = new byte[freeSpaceInfoBytes];
				metaFile.readFully(b);
				freeSpaceInfo = BitArrayWrapper.fromBytes(b);

				freeBlockCount = metaFile.readInt();
				usedBlockCount = metaFile.readInt();

				// read unit files from disk
				loadUnits();

			} else {
				int blockCnt = dataFile.getBlockCnt();
				freeSpaceInfo = new BitArrayWrapper(blockCnt);
				unitMap = new HashMap<Integer, Unit>();
				nextUnit = 1;
				deleteUnitFiles();

				byte[] block = new byte[blkSize];

				log.warn(String.format(
						"Repairing freespace information for %s after crash.",
						dataFileName));

				// the first block reserved, for compatibility with the code
				// which depends on the old IOMgr
				freeSpaceInfo.set(0);
				for (int i = 1; i < blockCnt; i++) {
					dataFile.read(i, block, 1);

					// read unitID
					// if zero -> block unused
					int unitID = Calc.toInt(block, 0);
					if (unitID > 0) {
						freeSpaceInfo.set(i);

						Unit unit = unitMap.get(unitID);
						if (unit == null) {
							// unit not yet loaded into the map
							unit = new Unit(getUnitFile(unitID),
									freeSpaceInfo.logicalSize());
							unitMap.put(unitID, unit);
							nextUnit = Math.max(nextUnit, unitID + 1);
						}
						unit.blockTable.set(i);
					}
				}

			}

			syncMeta(false, false);

			closed = false;
		} catch (FileException e) {
			throw new StoreException(e);
		} catch (IOException e) {
			throw new StoreException(e);
		}
		log.info("block space " + id + " initialized");
	}

	@Override
	public int read(int lba, byte[] buffer, int numBlocks)
			throws StoreException {
		return readImpl(lba, buffer, numBlocks);
	}

	private synchronized int readImpl(int lba, byte[] buffer, int numBlocks)
			throws StoreException {
		if (lba < 0 || lba >= freeSpaceInfo.logicalSize()) {
			throw new StoreException("invalid lba");
		}
		if (!freeSpaceInfo.get(lba)) {
			throw new StoreException("reading unused block: " + lba);
		}
		int readBlocks = 1;
		while ((readBlocks < numBlocks)
				&& (freeSpaceInfo.get(lba + readBlocks)))
			readBlocks++;
		try {
			dataFile.read(lba, buffer, readBlocks);
			// if (block[0] != BLOCK_IN_USE) {
			// log.warn("a released block loaded, this should not happen: " +
			// lba);
			// }
			return readBlocks;
		} catch (FileException e) {
			throw new StoreException(e);
		}
	}

	@Override
	public void write(int lba, byte[] buffer, int numBlocks)
			throws StoreException {
		writeImpl(lba, buffer, numBlocks);
	}

	private synchronized void writeImpl(int lba, byte[] block, int numBlocks)
			throws StoreException {
		if (lba < 0 || lba >= freeSpaceInfo.logicalSize()) {
			throw new StoreException("invalid lba");
		}
		try {
			// block[0] = 1;
			dataFile.write(lba, block, numBlocks);
		} catch (FileException e) {
			throw new StoreException(e);
		}
	}

	@Override
	public synchronized void sync() throws StoreException {
		syncMeta(false, false);
	}

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public synchronized int sizeOfBlock() throws StoreException {
		if (!closed) {
			return blkSize;
		} else {
			throw new StoreException("invalid state, block space is closed");
		}

	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public String getInfo() {
		StringBuilder out = new StringBuilder();
		int blocks = freeSpaceInfo.logicalSize();
		int blocksUsed = usedBlockCount;
		int blockSize = blkSize;

		out.append("#" + id + " " + dataFileName);
		out.append(" (" + (blocks * blockSize / 1024) + "KB)");
		out.append(", " + blockSize + "B blocks");
		out.append(", total " + (blocks - 1));
		out.append(", used " + blocksUsed + " ("
				+ ((blocksUsed * 100) / blocks) + "%)");
		out.append("\n");
		return out.toString();
	}

	@Override
	public int getInfoID() {
		return InfoContributor.NO_ID;
	}

	private void deleteUnitFiles() {
		List<UnitFile> unitFiles = getUnitFiles();
		for (UnitFile unitFile : unitFiles) {
			unitFile.file.delete();
		}
	}

	@Override
	public synchronized int createUnit(int unitID) throws StoreException {

		try {

			if (unitID <= 0) {
				// automatically assign unitID
				unitID = nextUnit++;
			} else {
				
				// check validity of requested unitID
				if (unitMap.get(unitID) != null) {
					throw new StoreException(String.format("The requested unitID %s already exists!", unitID));
				}
				
				nextUnit = Math.max(nextUnit, unitID + 1);
			}

			Unit unit = new Unit(getUnitFile(unitID),
					freeSpaceInfo.logicalSize());
			unitMap.put(unitID, unit);
			
			syncMeta(false, false);

			return unitID;

		} catch (FileNotFoundException e) {
			throw new StoreException(e);
		}
	}

	@Override
	public synchronized void dropUnit(int unitID) throws StoreException {

		try {

			// remove unit from main memory map
			Unit unit = unitMap.remove(unitID);
			if (unit == null) {
				throw new StoreException(String.format(
						"Unit with ID %s does not exist!", unitID));
			}
			
			// free blocks that belong to this unit
			Iterator<Integer> setBits = unit.blockTable.getSetBits();
			while (setBits.hasNext()) {
				freeBlock(setBits.next());
			}

			// close unit
			unit.close();
			// delete unit file
			File unitFile = getUnitFile(unitID);
			unitFile.delete();

			// TODO: syncMeta?

		} catch (IOException e) {
			throw new StoreException(e);
		}
	}

	private File getUnitFile(int unitID) {
		return new File(String.format(unitFileNameFormat, unitID));
	}

	private List<UnitFile> getUnitFiles() {

		File unitPrefix = new File(unitFileNamePrefix);
		File dir = unitPrefix.getParentFile();
		final String prefix = unitPrefix.getName();

		FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(prefix);
			}
		};

		// look for corresponding unit files
		File[] files = dir.listFiles(filter);
		ArrayList<UnitFile> unitFiles = new ArrayList<UnitFile>(files.length);
		for (File file : files) {
			// parse out unitID
			String unitIDString = file.getName().substring(prefix.length());
			try {

				int unitID = Integer.parseInt(unitIDString);
				unitFiles.add(new UnitFile(unitID, file));

			} catch (NumberFormatException e) {
				log.warn(String.format("Invalid unit file name found: %s",
						file.getPath()));
			}
		}

		return unitFiles;
	}

	private void loadUnits() throws IOException {

		unitMap = new HashMap<Integer, Unit>();
		nextUnit = 1;

		List<UnitFile> unitFiles = getUnitFiles();
		for (UnitFile unitFile : unitFiles) {

			unitMap.put(unitFile.unitID, Unit.fromFile(unitFile.file));
			nextUnit = Math.max(nextUnit, unitFile.unitID + 1);
		}
	}
}
