# panama-benchmarks
Benchmarks of new memory access and vector APIs in the Java Panama project.

**/AddBenchmark**
Benchmark the element-wise addition of two arrays of numbers. We test over standard Java arrays and (off-heap) native memory accessed via Unsafe and via the MemorySegment API. Using and not usingthe vector API.

**/SumBenchmark**
Benchmark the sum of all the elements in an array of numbers. We test over standard Java arrays and (off-heap) native memory accessed via Unsafe and via the MemorySegment API. Using and not usingthe vector API.