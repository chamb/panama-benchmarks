package com.activeviam.test;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorOperators;
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

/**
 * Benchmark the element wise aggregation of an array
 * of doubles into another array of doubles, using
 * combinations of  java arrays, byte buffers, standard java code
 * and the new Vector API.
 */
public class SumBenchmark {

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
            .include(SumBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    final static int SIZE = 1024;

    static final Arena GLOBAL_ARENA = Arena.global();

    @State(Scope.Benchmark)
    public static class Data {

        final double[] inputArray;
        final MemorySegment inputSegment;
        final long inputAddress;


        public Data() {
            this.inputArray = new double[SIZE];
            this.inputSegment = GLOBAL_ARENA.allocate(8 * SIZE);
            this.inputAddress = U.allocateMemory(8 * SIZE);
        }
    }

    @Benchmark
    public double scalarArray(Data state) {
        final double[] input = state.inputArray;
        double sum = 0;
        for(int i = 0; i < SIZE; i++) {
            sum += input[i];
        }
        return sum;
    }

    @Benchmark
    public double unrolledArray(Data state) {
        final double[] input = state.inputArray;

        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;
        for(int i = 0; i < SIZE; i+=4) {
            sum1 += input[i];
            sum2 += input[i];
            sum3 += input[i];
            sum4 += input[i];
        }
        return sum1 + sum2 + sum3 + sum4;
    }

    @Benchmark
    public double scalarUnsafe(Data state) {
        final long inputAddress = state.inputAddress;
        double sum = 0;
        for(int i = 0; i < SIZE; i++) {
            sum += U.getDouble(inputAddress + 8*i);
        }
        return sum;
    }

    @Benchmark
    public double unrolledUnsafe(Data state) {
        final long inputAddress = state.inputAddress;
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;
        for(int i = 0; i < SIZE; i++) {
            sum1 += U.getDouble(inputAddress + 8*i);
            sum2 += U.getDouble(inputAddress + 8*(i+1));
            sum3 += U.getDouble(inputAddress + 8*(i+2));
            sum4 += U.getDouble(inputAddress + 8*(i+3));
        }
        return sum1 + sum2 + sum3 + sum4;
    }

    final static VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_MAX;

    @Benchmark
    public double vectorArrayV1(Data state) {
        final double[] input = state.inputArray;
        DoubleVector sum = DoubleVector.broadcast(SPECIES, 0.0);
        for (int i = 0; i < input.length; i+=SPECIES.length()) {
            DoubleVector a = DoubleVector.fromArray(SPECIES, input, i);
            sum = sum.add(a);
        }
        return sum.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public double vectorArrayV2(Data state) {
        final double[] input = state.inputArray;
        double sum = 0.0;
        for (int i = 0; i < input.length; i+=SPECIES.length()) {
            sum += DoubleVector.fromArray(SPECIES, input, i).reduceLanes(VectorOperators.ADD);
        }
        return sum;
    }

    @Benchmark
    public double vectorSegmentV1(Data state) {
        final MemorySegment input = state.inputSegment;
        DoubleVector sum = DoubleVector.broadcast(SPECIES, 0.0);
        for (int i = 0; i < SIZE; i+=SPECIES.length()) {
            DoubleVector a = DoubleVector.fromMemorySegment(SPECIES, input, i, ByteOrder.nativeOrder());
            sum = sum.add(a);
        }
        return sum.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public double vectorSegmentV2(Data state) {
        final MemorySegment input = state.inputSegment;
        double sum = 0.0;
        for (int i = 0; i < SIZE; i+=SPECIES.length()) {
            sum += DoubleVector.fromMemorySegment(SPECIES, input, i, ByteOrder.nativeOrder()).reduceLanes(VectorOperators.ADD);
        }
        return sum;
    }

}
