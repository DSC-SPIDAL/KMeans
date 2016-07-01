#!/bin/bash
cp=$HOME/.m2/repository/com/google/guava/guava/19.0/guava-19.0.jar:$HOME/.m2/repository/com/intellij/annotations/12.0/annotations-12.0.jar:$HOME/sali/software/jdk1.8.0/jre/../lib/tools.jar:$HOME/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar:$HOME/.m2/repository/habanero-java-lib/habanero-java-lib/0.1.4-SNAPSHOT/habanero-java-lib-0.1.4-SNAPSHOT.jar:$HOME/.m2/repository/net/java/dev/jna/jna/4.1.0/jna-4.1.0.jar:$HOME/.m2/repository/net/java/dev/jna/jna-platform/4.1.0/jna-platform-4.1.0.jar:$HOME/.m2/repository/net/openhft/affinity/3.0/affinity-3.0.jar:$HOME/.m2/repository/net/openhft/compiler/2.2.4/compiler-2.2.4.jar:$HOME/.m2/repository/net/openhft/lang/6.8.2/lang-6.8.2.jar:$HOME/.m2/repository/ompi/ompijavabinding/1.10.1/ompijavabinding-1.10.1.jar:$HOME/.m2/repository/org/kohsuke/jetbrains/annotations/9.0/annotations-9.0.jar:$HOME/.m2/repository/org/ow2/asm/asm/5.0.4/asm-5.0.4.jar:$HOME/.m2/repository/org/slf4j/slf4j-api/1.7.12/slf4j-api-1.7.12.jar:$HOME/.m2/repository/org/xerial/snappy/snappy-java/1.1.2.1/snappy-java-1.1.2.1.jar:$HOME/sali/git/github/esaliya/java/KMeans/target/kmeans-1.0-spidal.jar

i=/N/u/sekanaya/sali/git/github/esaliya/java/KMeans/data/100n_10k/points.bin
n=100
d=2
b=true
o=/N/u/sekanaya/sali/git/github/esaliya/ccpp/KMeansC/data/100n_10k
t=bb
java -cp $cp org.saliya.ompi.kmeans.DataConverter -i $i -n $n -d $d -o $o -b $b -t $t
