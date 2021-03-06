#!/bin/bash
cp=$HOME/.m2/repository/com/google/guava/guava/19.0/guava-19.0.jar:$HOME/.m2/repository/com/intellij/annotations/12.0/annotations-12.0.jar:$HOME/sali/software/jdk1.8.0/jre/../lib/tools.jar:$HOME/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar:$HOME/.m2/repository/habanero-java-lib/habanero-java-lib/0.1.4-SNAPSHOT/habanero-java-lib-0.1.4-SNAPSHOT.jar:$HOME/.m2/repository/net/java/dev/jna/jna/4.1.0/jna-4.1.0.jar:$HOME/.m2/repository/net/java/dev/jna/jna-platform/4.1.0/jna-platform-4.1.0.jar:$HOME/.m2/repository/net/openhft/affinity/3.0/affinity-3.0.jar:$HOME/.m2/repository/net/openhft/compiler/2.2.4/compiler-2.2.4.jar:$HOME/.m2/repository/net/openhft/lang/6.8.2/lang-6.8.2.jar:$HOME/.m2/repository/ompi/ompijavabinding/1.10.1/ompijavabinding-1.10.1.jar:$HOME/.m2/repository/org/kohsuke/jetbrains/annotations/9.0/annotations-9.0.jar:$HOME/.m2/repository/org/ow2/asm/asm/5.0.4/asm-5.0.4.jar:$HOME/.m2/repository/org/slf4j/slf4j-api/1.7.12/slf4j-api-1.7.12.jar:$HOME/.m2/repository/org/xerial/snappy/snappy-java/1.1.2.1/snappy-java-1.1.2.1.jar:$HOME/sali/git/github/esaliya/java/KMeans/target/kmeans-1.0-spidal.jar


wd=$HOME/sali/git/github/esaliya/java/KMeans
c=$wd/data/100n_10k/centers.bin
p=$wd/data/100n_10k/points.bin
n=100
d=2
k=10
m=1000
t=0.00001
b=true
T=$1

nodes=$4
hostfile=$3

tpp=$T
ppn=$2

cps=12
spn=2
cpn=$(($cps*$spn))

pe=$(($cpn/$ppn))

pat="$tpp"x"$ppn"x"$nodes"

opts="-XX:+UseG1GC -Xms256m -Xmx"$5""$6""

# with IB
mpirun --map-by ppr:$ppn:node:PE=$pe -hostfile $hostfile -np $(($nodes*$ppn)) java $opts -cp $cp org.saliya.ompi.kmeans.Program -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T 2>&1 | tee "$pat"_"$n"_"$k"_"$d"_"$m".txt

# TCP (NO IB)
#mpirun --mca bt ^openib --report-bindings --map-by ppr:24:node -hostfile $hostfile -np $np java -cp $cp org.saliya.ompi.kmeans.Program -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T

