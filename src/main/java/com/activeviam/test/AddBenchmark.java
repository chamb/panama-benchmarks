package com.activeviam.test;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.nio.ByteOrder;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark the element wise aggregation of an array
 * of doubles into another array of doubles, using
 * combinations of  java arrays, byte buffers, standard java code
 * and the new Vector API.
 */
public class AddBenchmark {

    static final ValueLayout.OfDouble JAVA_DOUBLE = ValueLayout.JAVA_DOUBLE;

    static final Arena GLOBAL_ARENA = Arena.global();

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
                .mode(Mode.AverageTime)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    final static int SIZE = 1024;

    @State(Scope.Benchmark)
    public static class Data {
        final double[] inputArray;
        final double[] outputArray;
        final MemorySegment inputSegment;
        final MemorySegment outputSegment;
        final long inputAddress;
        final long outputAddress;
        double checksum;

        public Data() {
            this.inputArray = new double[SIZE];
            this.outputArray = new double[SIZE];

            this.inputSegment = GLOBAL_ARENA.allocate(8 * SIZE);
            this.outputSegment = GLOBAL_ARENA.allocate(8 * SIZE);

            this.inputAddress = U.allocateMemory(8 * SIZE);
            this.outputAddress = U.allocateMemory(8 * SIZE);

            this.checksum = 0;
            for (int i = 0; i < SIZE; i++) {
                this.inputArray[i] = i;
                this.inputSegment.setAtIndex(JAVA_DOUBLE, i, (double) i);
                this.outputSegment.setAtIndex(JAVA_DOUBLE, i, 0.0);
                U.putDouble(this.inputAddress, 8*i, (double) i);
                U.putDouble(this.outputAddress, 8*i, 0.0);
                checksum += i;
            }
            // System.out.println("Checksum: " + this.checksum);
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
    public void scalarArrayArrayLongStride(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        // long stride defeats automatic unrolling
        for(long i = 0; i < input.length; i+=1L) {
            output[(int) i] += input[(int) i];
        }
    }

    @Benchmark
    public void scalarSegmentSegment(Data state) {
        final MemorySegment input = state.inputSegment;
        final MemorySegment output = state.outputSegment;
        for(int i = 0; i < SIZE; i++) {
            output.setAtIndex(JAVA_DOUBLE, i, output.getAtIndex(JAVA_DOUBLE, i) + input.getAtIndex(JAVA_DOUBLE, i));
        }
    }

    @Benchmark
    public void scalarSegmentArray(Data state) {
        final MemorySegment input = state.inputSegment;
        final double[] output = state.outputArray;
        for(int i = 0; i < SIZE; i++) {
            output[i] += input.getAtIndex(JAVA_DOUBLE, i);
        }
    }

    @Benchmark
    public void unrolledSegmentArray(Data state) {
        final MemorySegment input = state.inputSegment;
        final double[] output = state.outputArray;
        for(int i = 0; i < SIZE; i+=4) {
            output[i] += input.getAtIndex(JAVA_DOUBLE, i);
            output[i+1] += input.getAtIndex(JAVA_DOUBLE, i+1);
            output[i+2] += input.getAtIndex(JAVA_DOUBLE, i+2);
            output[i+3] += input.getAtIndex(JAVA_DOUBLE, i+3);
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
    public void vectorSegmentArray(Data state) {
        final MemorySegment input = state.inputSegment;
        final double[] output = state.outputArray;
        for (int i = 0; i < SIZE; i+=SPECIES.length()) {
            DoubleVector a = DoubleVector.fromMemorySegment(SPECIES, input, 8*i, ByteOrder.nativeOrder());
            DoubleVector b = DoubleVector.fromArray(SPECIES, output, i);
            a = a.add(b);
            a.intoArray(output, i);
        }
    }

    @Benchmark
    public void vectorSegmentSegment(Data state) {
        final MemorySegment input = state.inputSegment;
        final MemorySegment output = state.outputSegment;
        for (int i = 0; i < SIZE; i+=SPECIES.length()) {
            DoubleVector a = DoubleVector.fromMemorySegment(SPECIES, input, 8*i, ByteOrder.nativeOrder());
            DoubleVector b = DoubleVector.fromMemorySegment(SPECIES, output, 8*i, ByteOrder.nativeOrder());
            a = a.add(b);
            a.intoMemorySegment(output, 8*i, ByteOrder.nativeOrder());
        }
    }

    @Benchmark
    public void vectorArraySegment(Data state) {
        final double[] input = state.inputArray;
        final MemorySegment output = state.outputSegment;

        for (int i = 0; i < input.length; i+=SPECIES.length()) {
            DoubleVector a = DoubleVector.fromArray(SPECIES, input, i);
            DoubleVector b = DoubleVector.fromMemorySegment(SPECIES, output, 8*i, ByteOrder.nativeOrder());
            a = a.add(b);
            a.intoMemorySegment(output, 8*i, ByteOrder.nativeOrder());
        }
    }
    
}
