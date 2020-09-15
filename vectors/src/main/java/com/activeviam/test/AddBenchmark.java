package com.activeviam.test;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Benchmark the element wise aggregation of an array
 * of doubles into another array of doubles, using
 * combinations of  java arrays, byte buffers, standard java code
 * and the new Vector API.
 */
public class AddBenchmark {

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
            .build();

        new Runner(opt).run();
    }

    final static int SIZE = 1024;

    @State(Scope.Benchmark)
    public static class Data {
        final double[] inputArray;
        final double[] outputArray;
        final byte[] inputByteArray;
        final byte[] outputByteArray;
        final ByteBuffer inputBuffer;
        final ByteBuffer outputBuffer;
        final long inputAddress;
        final long outputAddress;

        public Data() {
            this.inputArray = new double[SIZE];
            this.outputArray = new double[SIZE];
            this.inputByteArray = new byte[8 * SIZE];
            this.outputByteArray = new byte[8 * SIZE];
            this.inputAddress = U.allocateMemory(8 * SIZE);
            this.outputAddress = U.allocateMemory(8 * SIZE);
            this.inputBuffer = ByteBuffer.allocateDirect(8 * SIZE);
            this.outputBuffer = ByteBuffer.allocateDirect(8 * SIZE);
        }
    }

    @Benchmark
    public void scalarArrayArray(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        for(int i = 0; i < input.length; i++) {
            output[i] += input[i];
        }
    }

    @Benchmark
    public void unrolledArrayArray(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        for(int i = 0; i < input.length; i+=4) {
            output[i] += input[i];
            output[i+1] += input[i+1];
            output[i+2] += input[i+2];
            output[i+3] += input[i+3];
        }
    }

    @Benchmark
    public void scalarArrayArrayLong(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        // long stride defeats automatic unrolling
        for(long i = 0; i < input.length; i+=1L) {
            output[(int) i] += input[(int) i];
        }
    }

    @Benchmark
    public void scalarArrayBuffer(Data state) {
        final double[] input = state.inputArray;
        final ByteBuffer output = state.outputBuffer;
        for(int i = 0; i < input.length; i++) {
            output.putDouble(i << 3, output.getDouble(i << 3) + input[i]);
        }
    }

    @Benchmark
    public void scalarBufferArray(Data state) {
        final ByteBuffer input = state.inputBuffer;
        final double[] output = state.outputArray;
        for(int i = 0; i < input.capacity(); i+=8) {
            output[i >>> 3] += input.getDouble(i);
        }
    }

    @Benchmark
    public void scalarBufferBuffer(Data state) {
        final ByteBuffer input = state.inputBuffer;
        final ByteBuffer output = state.outputBuffer;
        for(int i = 0; i < input.capacity(); i+=8) {
            output.putDouble(i, output.getDouble(i) + input.getDouble(i));
        }
    }

    @Benchmark
    public void scalarUnsafeArray(Data state) {
        final long ia = state.inputAddress;
        final double[] output = state.outputArray;
        for(int i = 0; i < SIZE; i++) {
            output[i] += U.getDouble(ia + 8*i);
        }
    }

    @Benchmark
    public void unrolledUnsafeArray(Data state) {
        final long ia = state.inputAddress;
        final double[] output = state.outputArray;
        for(int i = 0; i < SIZE; i+=4) {
            output[i] += U.getDouble(ia + 8*i);
            output[i+1] += U.getDouble(ia + 8*(i+1));
            output[i+2] += U.getDouble(ia + 8*(i+2));
            output[i+3] += U.getDouble(ia + 8*(i+3));
        }
    }

    @Benchmark
    public void scalarUnsafeUnsafe(Data state) {
        final long ia = state.inputAddress;
        final long oa = state.outputAddress;
        for(int i = 0; i < SIZE; i++) {
            U.putDouble(oa + 8*i, U.getDouble(ia + 8*i) + U.getDouble(oa + 8*i));
        }
    }

    @Benchmark
    public void unrolledUnsafeUnsafe(Data state) {
        final long ia = state.inputAddress;
        final long oa = state.outputAddress;
        for(int i = 0; i < SIZE; i+=4) {
            U.putDouble(oa + 8*i, U.getDouble(ia + 8*i) + U.getDouble(oa + 8*i));
            U.putDouble(oa + 8*(i+1), U.getDouble(ia + 8*(i+1)) + U.getDouble(oa + 8*(i+1)));
            U.putDouble(oa + 8*(i+2), U.getDouble(ia + 8*(i+2)) + U.getDouble(oa + 8*(i+2)));
            U.putDouble(oa + 8*(i+3), U.getDouble(ia + 8*(i+3)) + U.getDouble(oa + 8*(i+3)));
        }
    }



    final static VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_MAX;

    @Benchmark
    public void vectorArrayArray(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;

        for (int i = 0; i < input.length; i+=SPECIES.length()) {
            DoubleVector a = DoubleVector.fromArray(SPECIES, input, i);
            DoubleVector b = DoubleVector.fromArray(SPECIES, output, i);
            a = a.add(b);
            a.intoArray(output, i);
        }
    }

    @Benchmark
    public void vectorByteArrayByteArray(Data state) {
        final byte[] input = state.inputByteArray;
        final byte[] output = state.outputByteArray;

        for (int i = 0; i < input.length; i += 8 * SPECIES.length()) {
            DoubleVector a = DoubleVector.fromByteArray(SPECIES, input, i, ByteOrder.nativeOrder());
            DoubleVector b = DoubleVector.fromByteArray(SPECIES, output, i, ByteOrder.nativeOrder());
            a = a.add(b);
            a.intoByteArray(output, i, ByteOrder.nativeOrder());
        }
    }

    @Benchmark
    public void vectorBufferBuffer(Data state) {
        final ByteBuffer input = state.inputBuffer;
        final ByteBuffer output = state.outputBuffer;
        for (int i = 0; i < input.capacity(); i += 8 * SPECIES.length()) {
            DoubleVector a = DoubleVector.fromByteBuffer(SPECIES, input, i, ByteOrder.nativeOrder());
            DoubleVector b = DoubleVector.fromByteBuffer(SPECIES, output, i, ByteOrder.nativeOrder());
            a = a.add(b);
            a.intoByteBuffer(output, i, ByteOrder.nativeOrder());
        }
    }

    @Benchmark
    public void vectorArrayBuffer(Data state) {
        final double[] input = state.inputArray;
        final ByteBuffer output = state.outputBuffer;

        for (int i = 0; i < input.length; i+=SPECIES.length()) {
            DoubleVector a = DoubleVector.fromArray(SPECIES, input, i);
            DoubleVector b = DoubleVector.fromByteBuffer(SPECIES, output, i << 3, ByteOrder.nativeOrder());
            a = a.add(b);
            a.intoByteBuffer(output, i << 3, ByteOrder.nativeOrder());
        }
    }

    @Benchmark
    public void vectorBufferArray(Data state) {
        final ByteBuffer input = state.inputBuffer;
        final double[] output = state.outputArray;
        for (int i = 0; i < input.capacity(); i += 8 * SPECIES.length()) {
            DoubleVector a = DoubleVector.fromByteBuffer(SPECIES, input, i, ByteOrder.nativeOrder());
            DoubleVector b = DoubleVector.fromArray(SPECIES, output, i >>> 3);
            a = a.add(b);
            a.intoArray(output, i >>> 3);
        }
    }
    
}
