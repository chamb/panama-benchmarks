package com.activeviam.test;

import static java.lang.foreign.ValueLayout.*;
import java.lang.foreign.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/**
 * Benchmark the element wise aggregation of an array
 * of doubles into another array of doubles, using
 * combinations of  java arrays, byte buffers, standard java code
 * and the new Vector API.
 */
public class AddBenchmark {

    static {
        System.setProperty("java.lang.foreign.Foreign","permit");
    }

    static final Unsafe U = getUnsafe();
    static Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Manually launch JMH */
    public static void main(String[] params) throws Exception {
        Options opt = new OptionsBuilder()
            .include(AddBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(5)
            .measurementIterations(5)
            .build();

        new Runner(opt).run();
    }

    final static int SIZE = 1024;

    @State(Scope.Benchmark)
    public static class Data {
        final double[] inputArray;
        final double[] outputArray;
        final float[] inputArrayFloat;
        final float[] outputArrayFloat;
        final byte[] inputBytes;
        final byte[] outputBytes;
        final long inputAddress;
        final long outputAddress;
        final MemorySegment inputSegment;
        final MemorySegment outputSegment;


        public Data() {
            this.inputArray = new double[SIZE];
            this.outputArray = new double[SIZE];
            this.inputArrayFloat = new float[SIZE];
            this.outputArrayFloat = new float[SIZE];
            this.inputBytes = new byte[8 * SIZE];
            this.outputBytes = new byte[8 * SIZE];
            this.inputAddress = U.allocateMemory(8 * SIZE);
            this.outputAddress = U.allocateMemory(8 * SIZE);
            this.inputSegment = MemorySegment.allocateNative(8*SIZE, MemorySession.global());
            this.outputSegment = MemorySegment.allocateNative(8*SIZE, MemorySession.global());
        }
    }

    @Benchmark
    public void scalarArray(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        for(int i = 0; i < SIZE; i++) {
            output[i] += input[i];
        }
    }

    @Benchmark
    public void scalarArray_float(Data state) {
        final float[] input = state.inputArrayFloat;
        final float[] output = state.outputArrayFloat;
        for(int i = 0; i < SIZE; i++) {
            output[i] += input[i];
        }
    }

    @Benchmark
    public void unrolledArray(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        for(int i = 0; i < SIZE; i+=4) {
            output[i] += input[i];
            output[i+1] += input[i+1];
            output[i+2] += input[i+2];
            output[i+3] += input[i+3];
        }
    }

    static final VarHandle AH = MethodHandles.arrayElementVarHandle(double[].class);
    static final VarHandle AHF = MethodHandles.arrayElementVarHandle(float[].class);

    @Benchmark
    public void scalarArrayHandle(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        for(int i = 0; i < input.length; i++) {
            AH.set(output, i, (double) AH.get(input, i) + (double) AH.get(output, i));
        }
    }

    @Benchmark
    public void scalarArrayHandle_float(Data state) {
        final float[] input = state.inputArrayFloat;
        final float[] output = state.outputArrayFloat;
        for(int i = 0; i < input.length; i++) {
            AHF.set(output, i, (float) AHF.get(input, i) + (float) AHF.get(output, i));
        }
    }

    @Benchmark
    public void unrolledArrayHandle(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        for(int i = 0; i < input.length; i+=4) {
            AH.set(output, i, (double) AH.get(input, i) + (double) AH.get(output, i));
            AH.set(output, i+1, (double) AH.get(input, i+1) + (double) AH.get(output, i+1));
            AH.set(output, i+2, (double) AH.get(input, i+2) + (double) AH.get(output, i+2));
            AH.set(output, i+3, (double) AH.get(input, i+3) + (double) AH.get(output, i+3));
        }
    }

    @Benchmark
    public void scalarUnsafe(Data state) {
        final long ia = state.inputAddress;
        final long oa = state.outputAddress;
        for(int i = 0; i < SIZE; i++) {
            U.putDouble(oa + 8*i, U.getDouble(ia + 8*i) + U.getDouble(oa + 8*i));
        }
    }

    @Benchmark
    public void manualLittleEndian(Data state) {
        final byte[] in = state.inputBytes;
        final byte[] out = state.outputBytes;
        if(in.length != out.length)
            throw new IllegalStateException("array sizes differend");

        for(int i = 0; i < in.length; i += 8) {
            double a = Double.longBitsToDouble((in[i] & 255L) | (in[i+1] & 255L) << 8
                     | (in[i+2] & 255L) << 16 | (in[i+3] & 255L) << 24
                     | (in[i+4] & 255L) << 32 | (in[i+5] & 255L) << 40
                     | (in[i+6] & 255L) << 48 | (in[i+7] & 255L) << 56
            );
            double b = Double.longBitsToDouble((out[i] & 255L) | (out[i+1] & 255L) << 8
                     | (out[i+2] & 255L) << 16 | (out[i+3] & 255L) << 24
                     | (out[i+4] & 255L) << 32 | (out[i+5] & 255L) << 40
                     | (out[i+6] & 255L) << 48 | (out[i+7] & 255L) << 56
            );

            long sum = Double.doubleToRawLongBits(a + b);
            out[i+0] = (byte)sum;
            out[i+1] = (byte)(sum >>> 8);
            out[i+2] = (byte)(sum >>> 16);
            out[i+3] = (byte)(sum >>> 24);
            out[i+4] = (byte)(sum >>> 32);
            out[i+5] = (byte)(sum >>> 40);
            out[i+6] = (byte)(sum >>> 48);
            out[i+7] = (byte)(sum >>> 56);
        }
    }

    @Benchmark
    public void unrolledUnsafe(Data state) {
        final long ia = state.inputAddress;
        final long oa = state.outputAddress;
        for(int i = 0; i < SIZE; i+=4) {
            U.putDouble(oa + 8*i, U.getDouble(ia + 8*i) + U.getDouble(oa + 8*i));
            U.putDouble(oa + 8*(i+1), U.getDouble(ia + 8*(i+1)) + U.getDouble(oa + 8*(i+1)));
            U.putDouble(oa + 8*(i+2), U.getDouble(ia + 8*(i+2)) + U.getDouble(oa + 8*(i+2)));
            U.putDouble(oa + 8*(i+3), U.getDouble(ia + 8*(i+3)) + U.getDouble(oa + 8*(i+3)));
        }
    }

    @Benchmark
    public void unrolledUnsafe_float(Data state) {
        final long ia = state.inputAddress;
        final long oa = state.outputAddress;
        for(int i = 0; i < SIZE; i+=4) {
            U.putFloat(oa + 4*i, U.getFloat(ia + 4*i) + U.getFloat(oa + 4*i));
            U.putFloat(oa + 4*(i+1), U.getFloat(ia + 4*(i+1)) + U.getFloat(oa + 4*(i+1)));
            U.putFloat(oa + 4*(i+2), U.getFloat(ia + 4*(i+2)) + U.getFloat(oa + 4*(i+2)));
            U.putFloat(oa + 4*(i+3), U.getFloat(ia + 4*(i+3)) + U.getFloat(oa + 4*(i+3)));
        }
    }

    static final VarHandle MHI_D = MemoryLayout.sequenceLayout(SIZE, ValueLayout.JAVA_DOUBLE)
            .varHandle(MemoryLayout.PathElement.sequenceElement());

    static final VarHandle MHI_F = MemoryLayout.sequenceLayout(SIZE, ValueLayout.JAVA_FLOAT)
            .varHandle(MemoryLayout.PathElement.sequenceElement());

    static final VarHandle MHI_L = MemoryLayout.sequenceLayout(SIZE, ValueLayout.JAVA_LONG)
            .varHandle(MemoryLayout.PathElement.sequenceElement());

    @Benchmark
    public void scalarSegmentHandle(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i++) {
            MHI_D.set(os, (long) i, (double) MHI_D.get(is, (long) i) + (double) MHI_D.get(os, (long) i));
        }
    }

    @Benchmark
    public void scalarSegment(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i++) {
            os.setAtIndex(JAVA_DOUBLE, (long) i, is.getAtIndex(JAVA_DOUBLE, (long) i) + is.getAtIndex(JAVA_DOUBLE, (long) i));
        }
    }

    @Benchmark
    public void unrolledSegment(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            os.setAtIndex(JAVA_DOUBLE, i, is.getAtIndex(JAVA_DOUBLE, i) + is.getAtIndex(JAVA_DOUBLE, i));
            os.setAtIndex(JAVA_DOUBLE, i+1, is.getAtIndex(JAVA_DOUBLE, i+1) + is.getAtIndex(JAVA_DOUBLE, i+1));
            os.setAtIndex(JAVA_DOUBLE, i+2, is.getAtIndex(JAVA_DOUBLE, i+2) + is.getAtIndex(JAVA_DOUBLE, i+2));
            os.setAtIndex(JAVA_DOUBLE, i+3, is.getAtIndex(JAVA_DOUBLE, i+3) + is.getAtIndex(JAVA_DOUBLE, i+3));
        }
    }

    @Benchmark
    public void unrolledSegmentHandle(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            MHI_D.set(os, (long) (i),   (double) MHI_D.get(is, (long) (i))   + (double) MHI_D.get(os, (long) (i)));
            MHI_D.set(os, (long) (i+1), (double) MHI_D.get(is, (long) (i+1)) + (double) MHI_D.get(os, (long) (i+1)));
            MHI_D.set(os, (long) (i+2), (double) MHI_D.get(is, (long) (i+2)) + (double) MHI_D.get(os, (long) (i+2)));
            MHI_D.set(os, (long) (i+3), (double) MHI_D.get(is, (long) (i+3)) + (double) MHI_D.get(os, (long) (i+3)));
        }
    }

    @Benchmark
    public void unrolledSegment_long(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            os.setAtIndex(JAVA_LONG, i, os.getAtIndex(JAVA_LONG, i) + is.getAtIndex(JAVA_LONG, i));
            os.setAtIndex(JAVA_LONG, i+1, os.getAtIndex(JAVA_LONG, i+1) + is.getAtIndex(JAVA_LONG, i+1));
            os.setAtIndex(JAVA_LONG, i+2, os.getAtIndex(JAVA_LONG, i+2) + is.getAtIndex(JAVA_LONG, i+2));
            os.setAtIndex(JAVA_LONG, i+3, os.getAtIndex(JAVA_LONG, i+3) + is.getAtIndex(JAVA_LONG, i+3));
        }
    }

    @Benchmark
    public void unrolledSegmentHandle_long(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            MHI_L.set(os, (long) (i),   (long) MHI_L.get(is, (long) (i))   + (long) MHI_L.get(os, (long) (i)));
            MHI_L.set(os, (long) (i+1), (long) MHI_L.get(is, (long) (i+1)) + (long) MHI_L.get(os, (long) (i+1)));
            MHI_L.set(os, (long) (i+2), (long) MHI_L.get(is, (long) (i+2)) + (long) MHI_L.get(os, (long) (i+2)));
            MHI_L.set(os, (long) (i+3), (long) MHI_L.get(is, (long) (i+3)) + (long) MHI_L.get(os, (long) (i+3)));
        }
    }

    @Benchmark
    public void unrolledSegmentHandle_float(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            MHI_F.set(os, (long) (i),   (float) MHI_F.get(is, (long) (i))   + (float) MHI_F.get(os, (long) (i)));
            MHI_F.set(os, (long) (i+1), (float) MHI_F.get(is, (long) (i+1)) + (float) MHI_F.get(os, (long) (i+1)));
            MHI_F.set(os, (long) (i+2), (float) MHI_F.get(is, (long) (i+2)) + (float) MHI_F.get(os, (long) (i+2)));
            MHI_F.set(os, (long) (i+3), (float) MHI_F.get(is, (long) (i+3)) + (float) MHI_F.get(os, (long) (i+3)));
        }
    }
}
