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
package org.brackit.server.node.index;

import java.math.BigDecimal;

import org.brackit.server.store.Field;
import org.brackit.server.util.Calc;
import org.brackit.xquery.ErrorCode;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.Dbl;
import org.brackit.xquery.atomic.Dec;
import org.brackit.xquery.atomic.Flt;
import org.brackit.xquery.atomic.Int;
import org.brackit.xquery.atomic.Int32;
import org.brackit.xquery.atomic.Int64;
import org.brackit.xquery.atomic.Numeric;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.expr.Cast;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Type;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class AtomicUtil {

	public static Field map(Type type) throws DocumentException {
		if (!type.isBuiltin()) {
			throw new DocumentException("%s is not a built-in type", type);
		}
		if (type.instanceOf(Type.STR)) {
			return Field.STRING;
		}
		if (type.isNumeric()) {
			if (type.instanceOf(Type.DBL)) {
				return Field.DOUBLE;
			}
			if (type.instanceOf(Type.FLO)) {
				return Field.FLOAT;
			}
			if (type.instanceOf(Type.INT)) {
				return Field.INTEGER;
			}
			if (type.instanceOf(Type.LON)) {
				return Field.LONG;
			}
			if (type.instanceOf(Type.INR)) {
				return Field.BIGDECIMAL;
			}
			if (type.instanceOf(Type.DEC)) {
				return Field.BIGDECIMAL;
			}
		}
		throw new DocumentException("Unsupported type: %s", type);
	}

	public static byte[] toBytes(Atomic atomic, Type type)
			throws DocumentException {
		if (atomic == null) {
			return null;
		}
		return toBytes(toType(atomic, type));
	}

	@Deprecated
	public static byte[] toBytes(String s, Type type) throws DocumentException {
		if (!type.isBuiltin()) {
			throw new DocumentException("%s is not a built-in type", type);
		}
		if (type.instanceOf(Type.STR)) {
			return Calc.fromString(s);
		}
		if (type.isNumeric()) {
			try {
				if (type.instanceOf(Type.DBL)) {
					return Calc.fromDouble(Double.parseDouble(s));
				}
				if (type.instanceOf(Type.FLO)) {
					return Calc.fromFloat(Float.parseFloat(s));
				}
				if (type.instanceOf(Type.INT)) {
					return Calc.fromInt(Integer.parseInt(s));
				}
				if (type.instanceOf(Type.LON)) {
					return Calc.fromLong(Long.parseLong(s));
				}
				if (type.instanceOf(Type.INR)) {
					return Calc.fromBigDecimal(new BigDecimal(s));
				}
				if (type.instanceOf(Type.DEC)) {
					return Calc.fromBigDecimal(new BigDecimal(s));
				}
			} catch (NumberFormatException e) {
				throw new DocumentException(new QueryException(
						ErrorCode.ERR_TYPE_INAPPROPRIATE_TYPE,
						"Illegal cast from %s to %s", Type.UNA, type));
			}
		}
		throw new DocumentException("Unsupported type: %s", type);
	}

	public static byte[] toBytes(Atomic atomic) throws DocumentException {
		if (atomic == null) {
			return null;
		}
		Type type = atomic.type();

		if (!type.isBuiltin()) {
			throw new DocumentException("%s is not a built-in type", type);
		}
		if (type.instanceOf(Type.STR)) {
			return Calc.fromString(atomic.stringValue());
		}
		if (type.isNumeric()) {
			if (type.instanceOf(Type.DBL)) {
				return Calc.fromDouble(((Numeric) atomic).doubleValue());
			}
			if (type.instanceOf(Type.FLO)) {
				return Calc.fromFloat(((Numeric) atomic).floatValue());
			}
			if (type.instanceOf(Type.INT)) {
				return Calc.fromInt(((Numeric) atomic).intValue());
			}
			if (type.instanceOf(Type.LON)) {
				return Calc.fromLong(((Numeric) atomic).longValue());
			}
			if (type.instanceOf(Type.INR)) {
				return Calc.fromBigDecimal(((Numeric) atomic).decimalValue());
			}
			if (type.instanceOf(Type.DEC)) {
				return Calc.fromBigDecimal(((Numeric) atomic).decimalValue());
			}
		}
		throw new DocumentException("Unsupported type: %s", type);
	}

	public static Atomic fromBytes(byte[] b, Type type)
			throws DocumentException {
		if (!type.isBuiltin()) {
			throw new DocumentException("%s is not a built-in type", type);
		}
		if (type.instanceOf(Type.STR)) {
			return new Str(Calc.toString(b));
		}
		if (type.isNumeric()) {
			if (type.instanceOf(Type.DBL)) {
				return new Dbl(Calc.toDouble(b));
			}
			if (type.instanceOf(Type.FLO)) {
				return new Flt(Calc.toFloat(b));
			}
			if (type.instanceOf(Type.INT)) {
				return new Int32(Calc.toInt(b));
			}
			if (type.instanceOf(Type.LON)) {
				return new Int64(Calc.toLong(b));
			}
			if (type.instanceOf(Type.INR)) {
				return new Int(Calc.toBigDecimal(b));
			}
			if (type.instanceOf(Type.DEC)) {
				return new Dec(Calc.toBigDecimal(b));
			}
		}
		throw new DocumentException("Unsupported type: %s", type);
	}

	public static Atomic toType(Atomic atomic, Type type)
			throws DocumentException {

		try {
			return Cast.cast(null, atomic, type);
		} catch (QueryException e) {
			throw new DocumentException(e);
		}
	}
}
