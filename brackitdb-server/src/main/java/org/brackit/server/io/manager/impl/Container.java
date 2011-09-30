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
package org.brackit.server.io.manager.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.brackit.server.io.buffer.BufferException;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class Container {

	private final File dir;

	private final int cntID;

	private final int blkSize;

	private final int bufSize;

	private final int iniSize;

	private final int extSize;

	public Container(File dir, int cntID, int bufSize, int blkSize,
			int iniSize, int extSize) {
		super();
		this.dir = dir;
		this.cntID = cntID;
		this.bufSize = bufSize;
		this.blkSize = blkSize;
		this.iniSize = iniSize;
		this.extSize = extSize;
	}

	public Container(File dir) throws BufferException {
		try {
			this.dir = dir;
			File file = new File(dir.getAbsolutePath() + File.separator
					+ "cnt.cfg");
			FileInputStream fin = new FileInputStream(file);
			BufferedInputStream bin = new BufferedInputStream(fin);
			cntID = readInt(bin);
			bufSize = readInt(bin);
			blkSize = readInt(bin);
			iniSize = readInt(bin);
			extSize = readInt(bin);
			bin.close();
		} catch (IOException e) {
			throw new BufferException(e);
		}
	}

	public File getDir() {
		return dir;
	}

	public int getCntID() {
		return cntID;
	}

	public int getBlkSize() {
		return blkSize;
	}

	public int getBufSize() {
		return bufSize;
	}

	public int getIniSize() {
		return iniSize;
	}

	public int getExtSize() {
		return extSize;
	}

	public void write() throws BufferException {

		try {
			File file = new File(dir.getAbsolutePath() + File.separator
					+ "cnt.cfg");
			FileOutputStream fout = new FileOutputStream(file);
			BufferedOutputStream bout = new BufferedOutputStream(fout);
			writeInt(bout, cntID);
			writeInt(bout, bufSize);
			writeInt(bout, blkSize);
			writeInt(bout, iniSize);
			writeInt(bout, extSize);
			bout.close();
		} catch (IOException e) {
			throw new BufferException(e);
		}
	}

	private void writeInt(OutputStream out, int i) throws IOException {
		out.write((byte) ((i >> 24) & 255));
		out.write((byte) ((i >> 16) & 255));
		out.write((byte) ((i >> 8) & 255));
		out.write((byte) (i & 255));
	}

	private int readInt(InputStream out) throws IOException {
		int i = 0;
		i |= ((out.read() & 255) << 24);
		i |= ((out.read() & 255) << 16);
		i |= ((out.read() & 255) << 8);
		i |= ((out.read() & 255));
		return i;
	}
}
