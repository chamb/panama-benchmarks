package com.activeviam.test;

import static jdk.incubator.foreign.MemoryAccess.*;
import jdk.incubator.foreign.*;
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
        System.setProperty("jdk.incubator.foreign.Foreign","permit");
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
        final long inputAddress;
        final long outputAddress;
        final MemorySegment inputSegment;
        final MemorySegment outputSegment;


        public Data() {
            this.inputArray = new double[SIZE];
            this.outputArray = new double[SIZE];
            this.inputAddress = U.allocateMemory(8 * SIZE);
            this.outputAddress = U.allocateMemory(8 * SIZE);
            this.inputSegment = MemoryAddress.ofLong(inputAddress).asSegmentRestricted(8*SIZE);
            this.outputSegment = MemoryAddress.ofLong(outputAddress).asSegmentRestricted(8*SIZE);
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

    @Benchmark
    public void scalarArrayHandle(Data state) {
        final double[] input = state.inputArray;
        final double[] output = state.outputArray;
        for(int i = 0; i < input.length; i++) {
            AH.set(output, i, (double) AH.get(input, i) + (double) AH.get(output, i));
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

    static final VarHandle MHI = MemoryLayout.ofSequence(SIZE, MemoryLayouts.JAVA_DOUBLE)
            .varHandle(double.class, MemoryLayout.PathElement.sequenceElement());

    @Benchmark
    public void scalarMHI(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i++) {
            MHI.set(os, (long) i, (double) MHI.get(is, (long) i) + (double) MHI.get(os, (long) i));
        }
    }

    @Benchmark
    public void scalarMHI_v2(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i++) {
            setDoubleAtIndex(os, i,getDoubleAtIndex(is, i) + getDoubleAtIndex(os, i));
        }
    }

    @Benchmark
    public void unrolledMHI(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            MHI.set(os, (long) (i),   (double) MHI.get(is, (long) (i))   + (double) MHI.get(os, (long) (i)));
            MHI.set(os, (long) (i+1), (double) MHI.get(is, (long) (i+1)) + (double) MHI.get(os, (long) (i+1)));
            MHI.set(os, (long) (i+2), (double) MHI.get(is, (long) (i+2)) + (double) MHI.get(os, (long) (i+2)));
            MHI.set(os, (long) (i+3), (double) MHI.get(is, (long) (i+3)) + (double) MHI.get(os, (long) (i+3)));
        }
    }

    @Benchmark
    public void unrolledMHI_v2(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            setDoubleAtIndex(os, i,getDoubleAtIndex(is, i) + MemoryAccess.getDoubleAtIndex(os, i));
            setDoubleAtIndex(os, i+1,getDoubleAtIndex(is, i+1) + getDoubleAtIndex(os, i+1));
            setDoubleAtIndex(os, i+2,getDoubleAtIndex(is, i+2) + getDoubleAtIndex(os, i+2));
            setDoubleAtIndex(os, i+3,getDoubleAtIndex(is, i+3) + getDoubleAtIndex(os, i+3));
        }
    }

}
