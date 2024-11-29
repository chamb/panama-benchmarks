## Quick Start

Build the package with Maven 2.

    mvn package

Run the `-h` flag to see your options.

    java --enable-preview -jar target/benchmarks-mem.jar -h

A quick bench could be as follows.

    java --enable-preview -jar target/benchmarks-mem.jar -i 1 -wi 1 -w 1 -t 1 -r 1 -f 0 -tu us
