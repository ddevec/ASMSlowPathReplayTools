/**
 * saeg-commons: common utility methods
 * Copyright (c) 2014, 2016 University of Sao Paulo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.ddevec.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Iterator;

public class ArrayUtils {

    private ArrayUtils() {
        // No instances
    }

    public static <T> int indexOf(final T[] array, final T object) {
        if (object == null) {
            for (int i = 0; i < array.length; i++) {
                if (array[i] == null)
                    return i;
            }
        } else {
            for (int i = 0; i < array.length; i++) {
                if (object.equals(array[i]))
                    return i;
            }
        }
        return -1;
    }

    public static int indexOf(final byte[] array, final byte element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOf(final short[] array, final short element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOf(final int[] array, final int element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOf(final long[] array, final long element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOf(final float[] array, final float element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOf(final double[] array, final double element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOf(final boolean[] array, final boolean element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOf(final char[] array, final char element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static <T> boolean contains(final T[] array, final Object object) {
        return indexOf(array, object) != -1;
    }

    public static boolean contains(final byte[] array, final byte element) {
        return indexOf(array, element) != -1;
    }

    public static boolean contains(final short[] array, final short element) {
        return indexOf(array, element) != -1;
    }

    public static boolean contains(final int[] array, final int element) {
        return indexOf(array, element) != -1;
    }

    public static boolean contains(final long[] array, final long element) {
        return indexOf(array, element) != -1;
    }

    public static boolean contains(final float[] array, final float element) {
        return indexOf(array, element) != -1;
    }

    public static boolean contains(final double[] array, final double element) {
        return indexOf(array, element) != -1;
    }

    public static boolean contains(final boolean[] array, final boolean element) {
        return indexOf(array, element) != -1;
    }

    public static boolean contains(final char[] array, final char element) {
        return indexOf(array, element) != -1;
    }

    public static void merge(
            final Object dest, final int destPos,
            final Object array1, final int srcPos1, final int length1,
            final Object array2, final int srcPos2, final int length2) {
        System.arraycopy(array1, srcPos1, dest, destPos, length1);
        System.arraycopy(array2, srcPos2, dest, destPos + length1, length2);
    }

    public static <T> T[] merge(final T[] array1, final T[] array2) {
        final T[] result = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    public static byte[] merge(final byte[] array1, final byte[] array2) {
        final byte[] result = new byte[array1.length + array2.length];
        merge(result, 0, array1, 0, array1.length, array2, 0, array2.length);
        return result;
    }

    public static short[] merge(final short[] array1, final short[] array2) {
        final short[] result = new short[array1.length + array2.length];
        merge(result, 0, array1, 0, array1.length, array2, 0, array2.length);
        return result;
    }

    public static int[] merge(final int[] array1, final int[] array2) {
        final int[] result = new int[array1.length + array2.length];
        merge(result, 0, array1, 0, array1.length, array2, 0, array2.length);
        return result;
    }

    public static long[] merge(final long[] array1, final long[] array2) {
        final long[] result = new long[array1.length + array2.length];
        merge(result, 0, array1, 0, array1.length, array2, 0, array2.length);
        return result;
    }

    public static float[] merge(final float[] array1, final float[] array2) {
        final float[] result = new float[array1.length + array2.length];
        merge(result, 0, array1, 0, array1.length, array2, 0, array2.length);
        return result;
    }

    public static double[] merge(final double[] array1, final double[] array2) {
        final double[] result = new double[array1.length + array2.length];
        merge(result, 0, array1, 0, array1.length, array2, 0, array2.length);
        return result;
    }

    public static boolean[] merge(final boolean[] array1, final boolean[] array2) {
        final boolean[] result = new boolean[array1.length + array2.length];
        merge(result, 0, array1, 0, array1.length, array2, 0, array2.length);
        return result;
    }

    public static char[] merge(final char[] array1, final char[] array2) {
        final char[] result = new char[array1.length + array2.length];
        merge(result, 0, array1, 0, array1.length, array2, 0, array2.length);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] merge(final T[] first, final T[]... arrays) {
        int length = first.length;
        for (int i = 0; i < arrays.length; i++) {
            length = length + arrays[i].length;
        }
        final T[] result = Arrays.copyOf(first, length);
        int offset = first.length;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static byte[] merge(final byte[] first, final byte[]... arrays) {
        int length = first.length;
        for (int i = 0; i < arrays.length; i++) {
            length = length + arrays[i].length;
        }
        final byte[] result = Arrays.copyOf(first, length);
        int offset = first.length;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static short[] merge(final short[] first, final short[]... arrays) {
        int length = first.length;
        for (int i = 0; i < arrays.length; i++) {
            length = length + arrays[i].length;
        }
        final short[] result = Arrays.copyOf(first, length);
        int offset = first.length;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static int[] merge(final int[] first, final int[]... arrays) {
        int length = first.length;
        for (int i = 0; i < arrays.length; i++) {
            length = length + arrays[i].length;
        }
        final int[] result = Arrays.copyOf(first, length);
        int offset = first.length;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static long[] merge(final long[] first, final long[]... arrays) {
        int length = first.length;
        for (int i = 0; i < arrays.length; i++) {
            length = length + arrays[i].length;
        }
        final long[] result = Arrays.copyOf(first, length);
        int offset = first.length;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static float[] merge(final float[] first, final float[]... arrays) {
        int length = first.length;
        for (int i = 0; i < arrays.length; i++) {
            length = length + arrays[i].length;
        }
        final float[] result = Arrays.copyOf(first, length);
        int offset = first.length;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static double[] merge(final double[] first, final double[]... arrays) {
        int length = first.length;
        for (int i = 0; i < arrays.length; i++) {
            length = length + arrays[i].length;
        }
        final double[] result = Arrays.copyOf(first, length);
        int offset = first.length;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static boolean[] merge(final boolean[] first, final boolean[]... arrays) {
        int length = first.length;
        for (int i = 0; i < arrays.length; i++) {
            length = length + arrays[i].length;
        }
        final boolean[] result = Arrays.copyOf(first, length);
        int offset = first.length;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static char[] merge(final char[] first, final char[]... arrays) {
        int length = first.length;
        for (int i = 0; i < arrays.length; i++) {
            length = length + arrays[i].length;
        }
        final char[] result = Arrays.copyOf(first, length);
        int offset = first.length;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, offset, arrays[i].length);
            offset += arrays[i].length;
        }
        return result;
    }

    public static byte[] toArray(final Collection<Byte> coll, byte[] array) {
        if (array.length < coll.size()) {
            array = new byte[coll.size()];
        }
        final Iterator<Byte> it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            array[i++] = it.next().byteValue();
        }
        return array;
    }

    public static short[] toArray(final Collection<Short> coll, short[] array) {
        if (array.length < coll.size()) {
            array = new short[coll.size()];
        }
        final Iterator<Short> it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            array[i++] = it.next().shortValue();
        }
        return array;
    }

    public static int[] toArray(final Collection<Integer> coll, int[] array) {
        if (array.length < coll.size()) {
            array = new int[coll.size()];
        }
        final Iterator<Integer> it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            array[i++] = it.next().intValue();
        }
        return array;
    }

    public static long[] toArray(final Collection<Long> coll, long[] array) {
        if (array.length < coll.size()) {
            array = new long[coll.size()];
        }
        final Iterator<Long> it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            array[i++] = it.next().longValue();
        }
        return array;
    }

    public static float[] toArray(final Collection<Float> coll, float[] array) {
        if (array.length < coll.size()) {
            array = new float[coll.size()];
        }
        final Iterator<Float> it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            array[i++] = it.next().floatValue();
        }
        return array;
    }

    public static double[] toArray(final Collection<Double> coll, double[] array) {
        if (array.length < coll.size()) {
            array = new double[coll.size()];
        }
        final Iterator<Double> it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            array[i++] = it.next().doubleValue();
        }
        return array;
    }

    public static boolean[] toArray(final Collection<Boolean> coll, boolean[] array) {
        if (array.length < coll.size()) {
            array = new boolean[coll.size()];
        }
        final Iterator<Boolean> it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            array[i++] = it.next().booleanValue();
        }
        return array;
    }

    public static char[] toArray(final Collection<Character> coll, char[] array) {
        if (array.length < coll.size()) {
            array = new char[coll.size()];
        }
        final Iterator<Character> it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            array[i++] = it.next().charValue();
        }
        return array;
    }
}

