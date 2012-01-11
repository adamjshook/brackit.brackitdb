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
package org.brackit.server.tx.locking.protocol;

/**
 * @author Sebastian Baechle
 * 
 */
public class LockProtocolUtil {
	public static <T extends LockMode<T>> String dump(LockProtocol<T> mode) {
		StringBuilder dump = new StringBuilder(mode.getClass().getName());
		dump.append("\n\n");
		dump.append("Compatibility Matrix:\n");
		dump.append(dumpCompatibilityMatrix(mode));
		dump.append("\n\n");
		dump.append("Conversion Matrix:\n");
		dump.append(dumpConversionMatrix(mode));

		return dump.toString();
	}

	public static <T extends LockMode<T>> void testConversionMatrix(
			LockProtocol<T> mode) {
		for (T requested : mode.modes()) {
			for (T granted : mode.modes()) {
				T conversion = granted.convert(requested);

				// conversion mode must at least be incompatible with all modes
				// either granted or requested is incompatible
				for (T other : mode.modes()) {
					if ((!granted.isCompatible(other))
							&& (conversion.isCompatible(other))) {
						System.err
								.println(String
										.format(
												"Conversion %s + %s -> %s is illegal because %s is not compatible with %s but %s is.",
												granted, requested, conversion,
												granted, other, conversion));
					}
				}

				for (T other : mode.modes()) {
					if ((!requested.isCompatible(other))
							&& (conversion.isCompatible(other))) {
						System.err
								.println(String
										.format(
												"Conversion %s + %s -> %s is illegal because %s is not compatible with %s but %s is.",
												granted, requested, conversion,
												requested, other, conversion));
					}
				}
			}
		}
	}

	public static <T extends LockMode<T>> String dumpCompatibilityMatrix(
			LockProtocol<T> mode) {
		StringBuilder dump = new StringBuilder();

		// determine maximum length of mode string
		int maxLength = 1;
		for (T m : mode.modes()) {
			maxLength = Math.max(maxLength, m.toString().length());
		}

		String columnHeader = String.format("| %%-%ss ||", maxLength);
		String column = String.format(" %%-%ss |", maxLength);

		dump.append(String.format(columnHeader, ""));
		for (T m : mode.modes()) {
			dump.append(String.format(column, m));
		}

		int rowLength = dump.length();

		dump.append("\n");
		for (int i = 0; i < rowLength; i++) {
			dump.append("=");
		}

		for (T requested : mode.modes()) {
			dump.append("\n");
			dump.append(String.format(columnHeader, requested));
			for (T granted : mode.modes()) {
				dump.append(String.format(column, granted
						.isCompatible(requested) ? "+" : "-"));
			}
		}

		return dump.toString();
	}

	private static <T extends LockMode<T>> String dumpConversionMatrix(
			LockProtocol<T> mode) {
		StringBuilder dump = new StringBuilder();

		// determine maximum length of mode string
		int maxLength = 1;
		for (T m : mode.modes()) {
			maxLength = Math.max(maxLength, m.toString().length());
		}

		String columnHeader = String.format("| %%-%ss ||", maxLength);
		String column = String.format(" %%-%ss |", maxLength);

		dump.append(String.format(columnHeader, ""));
		for (T m : mode.modes()) {
			dump.append(String.format(column, m));
		}

		int rowLength = dump.length();

		dump.append("\n");
		for (int i = 0; i < rowLength; i++) {
			dump.append("=");
		}

		for (T requested : mode.modes()) {
			dump.append("\n");
			dump.append(String.format(columnHeader, requested));
			for (T granted : mode.modes()) {
				dump.append(String.format(column, granted.convert(requested)));
			}
		}

		return dump.toString();
	}
}
