/*
 * Copyright (c) 2001-2008 Caucho Technology, Inc.  All rights reserved.
 *
 * The Apache Software License, Version 1.1
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Caucho Technology (http://www.caucho.com/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "Burlap", "Resin", and "Caucho" must not be used to
 *    endorse or promote products derived from this software without prior
 *    written permission. For written permission, please contact
 *    info@caucho.com.
 *
 * 5. Products derived from this software may not be called "Resin"
 *    nor may "Resin" appear in their names without prior written
 *    permission of Caucho Technology.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL CAUCHO TECHNOLOGY OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Scott Ferguson
 */

package com.alibaba.com.caucho.hessian.util;

/**
 * The IntMap provides a simple hashmap from keys to integers.  The API is
 * an abbreviation of the HashMap collection API.
 * <p>
 * <p>The convenience of IntMap is avoiding all the silly wrapping of
 * integers.
 */
public class IntMap {
    /**
     * Encoding of a null entry.  Since NULL is equal to Integer.MIN_VALUE,
     * it's impossible to distinguish between the two.
     */
    public final static int NULL = 0xdeadbeef; // Integer.MIN_VALUE + 1;

    private static final Object DELETED = new Object();

    private Object[] _keys;
    private int[] _values;

    private int _size;
    private int _mask;

    /**
     * Create a new IntMap.  Default size is 16.
     */
    public IntMap(int capacity) {
        capacity = tableSizeFor(capacity);
        _keys = new Object[capacity];
        _values = new int[capacity];

        _mask = capacity - 1;
        _size = 0;
    }

    private int tableSizeFor(int capacity) {
        int number = 1;
        while (number < capacity) {
            number = number << 1;
        }
        return number <= 8 ? 16 : number;
    }

    /**
     * Clear the hashmap.
     */
    public void clear() {
        Object[] keys = _keys;
        int[] values = _values;

        for (int i = keys.length - 1; i >= 0; i--) {
            keys[i] = null;
            values[i] = 0;
        }

        _size = 0;
    }

    /**
     * Returns the current number of entries in the map.
     */
    public int size() {
        return _size;
    }

    /**
     * Puts a new value in the property table with the appropriate flags
     */
    public int get(Object key) {
        int keyIdx = findKey(key);
        if (keyIdx >= 0) {
            return _values[keyIdx];
        } else {
            return NULL;
        }
    }

    private int findKey(final Object key) {
        final int h = hash(key);
        final Object[] keys = this._keys;
        final int mask = _mask;

        final int LOOP_UNROLLING = 3;
        for (int i = 0; i < LOOP_UNROLLING; i++) {
            int idx = (h + i) & mask;
            Object _key = keys[idx];
            if (key.equals(_key)) {
                return idx;
            } else if (_key == null) {
                return -1;
            }
        }

        for (int i = LOOP_UNROLLING; i < keys.length; i++) {
            int idx = (h + i) & mask;
            Object _key = keys[idx];
            if (key.equals(_key)) {
                return idx;
            } else if (_key == null) {
                return -1;
            }
        }

        return -1;
    }

    private int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    /**
     * Expands the property table
     */
    private void resize() {
        Object[] oldKeys = _keys;
        int[] oldVals = _values;

        _keys = new Object[oldKeys.length << 1];
        _values = new int[oldKeys.length << 1];
        _mask = _keys.length - 1;
        _size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != null && oldKeys[i] != DELETED) {
                put0(oldKeys[i], oldVals[i]);
            }
        }
    }

    /**
     * Puts a new value in the property table with the appropriate flags
     */
    public int put(Object key, int value) {
        int size = _size;
        if (size + (size >> 1) >= _keys.length) {//rehash
            resize();
        }
        return put0(key, value);
    }

    private int put0(Object key, int value) {
        int h = hash(key);
        int mask = _mask;
        Object[] keys = this._keys;
        int[] values = this._values;
        for (int i = 0; i < keys.length; i++) {
            int idx = (h + i) & mask;
            Object testKey = keys[idx];
            if (testKey == null || testKey == DELETED) {
                keys[idx] = key;
                values[idx] = value;
                _size++;
                return NULL;
            } else if (key.equals(testKey)) {//replace
                int oldV = values[idx];
                values[idx] = value;
                return oldV;
            }
        }
        return NULL;
    }

    /**
     * Deletes the entry.  Returns true if successful.
     */
    public int remove(Object key) {
        int keyIdx = findKey(key);
        if (keyIdx >= 0) {
            _size--;

            _keys[keyIdx] = DELETED;
            return _values[keyIdx];
        } else {
            return NULL;
        }
    }

    @Override
    public String toString() {
        StringBuilder sbuf = new StringBuilder();

        sbuf.append("IntMap[");
        boolean isFirst = true;
        for (int i = 0; i <= _mask; i++) {
            if (_keys[i] != null && _keys[i] != DELETED) {
                if (!isFirst) {
                    sbuf.append(", ");
                }

                isFirst = false;
                sbuf.append(_keys[i]);
                sbuf.append(':');
                sbuf.append(_values[i]);
            }
        }
        sbuf.append(']');

        return sbuf.toString();
    }
}
