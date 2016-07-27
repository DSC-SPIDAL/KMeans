#!/bin/bash
cp=$HOME/.m2/repository/com/google/guava/guava/19.0/guava-19.0.jar:$HOME/.m2/repository/com/intellij/annotations/12.0/annotations-12.0.jar:$HOME/sali/software/jdk1.8.0/jre/../lib/tools.jar:$HOME/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar:$HOME/.m2/repository/habanero-java-lib/habanero-java-lib/0.1.4-SNAPSHOT/habanero-java-lib-0.1.4-SNAPSHOT.jar:$HOME/.m2/repository/net/java/dev/jna/jna/4.2.1/jna-4.2.1.jar:$HOME/.m2/repository/net/java/dev/jna/jna-platform/4.2.1/jna-platform-4.2.1.jar:$HOME/.m2/repository/net/openhft/affinity/3.0/affinity-3.0.jar:$HOME/.m2/repository/net/openhft/compiler/2.2.4/compiler-2.2.4.jar:$HOME/.m2/repository/net/openhft/lang/6.8.2/lang-6.8.2.jar:$HOME/.m2/repository/ompi/ompijavabinding/1.10.1/ompijavabinding-1.10.1.jar:$HOME/.m2/repository/org/kohsuke/jetbrains/annotations/9.0/annotations-9.0.jar:$HOME/.m2/repository/org/ow2/asm/asm/5.0.4/asm-5.0.4.jar:$HOME/.m2/repository/org/slf4j/slf4j-api/1.7.12/slf4j-api-1.7.12.jar:$HOME/.m2/repository/org/xerial/snappy/snappy-java/1.1.2.1/snappy-java-1.1.2.1.jar:$HOME/sali/git/github/esaliya/java/KMeans/target/kmeans-1.0-spidal-lrt.jar:~/sali/software/slf4j-1.7.21/slf4j-api-1.7.21.jar:~/sali/software/slf4j-1.7.21/slf4j-simple-1.7.21.jar



:<<COMMENT
#original data
wd=/N/u/sekanaya/sali/git/github/esaliya/java/KMeans
c=$wd/centers.bin
p=$wd/points.bin
#p=$wd/points_X2.bin
n=1000000
#n=2000000
d=2
k=1000
m=1000
t=0.00001
b=true
T=$1
COMMENT

centers=(1000 10000 50000 100000 500000)
#centers=(10000)

for cen in ${centers[@]}; do
  wd=/N/u/sekanaya/sali/projects/flink/kmeans/data2
  points=1000000
  exp="$points"_"$cen"
  p=$wd/$exp/points_BE.bin
  c=$wd/$exp/centers_BE.bin
  n=$points
  d=2
  k=$cen
  m=100
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

  #opts="-XX:+UseG1GC -Xms256m -Xmx"$5""$6""
  opts="-XX:+UseSerialGC -Xms256m -Xmx"$5""$6""

  explicitbind=$7
  procbind=$8

  reportmpibindings=--report-bindings
  #reportmpibindings=
  #btl="-v --display-map --mca btl tcp,self,sm --mca btl_tcp_if_include eth1 --mca pml ob1"
  #btl="--mca btl tcp,self,sm"
  #btl="--mca btl openib,self,sm"
  btl="--mca btl ^tcp"

  if [ $procbind = "core" ]; then
      # with IB and bound to corresponding PEs
      mpirun $btl --report-bindings --map-by ppr:$ppn:node:PE=$pe --bind-to core -hostfile $hostfile -np $(($nodes*$ppn)) java $opts -cp $cp org.saliya.ompi.kmeans.ProgramLRT -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T -bind $explicitbind 2>&1 | tee lrt_"$pat"_"$n"_"$k"_"$d"_"$m".txt
  elif [ $procbind = "socket" ]; then
     # with IB and bound to socket
     mpirun $btl --report-bindings --map-by ppr:$ppn:node --bind-to socket -hostfile $hostfile -np $(($nodes*$ppn)) java $opts -cp $cp org.saliya.ompi.kmeans.ProgramLRT -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T -bind $explicitbind 2>&1 | tee lrt_"$pat"_"$n"_"$k"_"$d"_"$m".txt
  else
      # with IB but bound to none
      #with pontus pvtm
      #mpirun $btl --report-bindings --map-by ppr:$ppn:node --bind-to none -hostfile $hostfile -np $(($nodes*$ppn)) ./lrt.run.internal.sh "$opts" "$cp" $n $d $k $t $c $p $m $b $T $explicitbind $pat
      mpirun $btl --report-bindings --map-by ppr:$ppn:node --bind-to none -hostfile $hostfile -np $(($nodes*$ppn)) java $opts -cp $cp org.saliya.ompi.kmeans.ProgramLRT -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T -bind $explicitbind 2>&1 | tee lrt_"$pat"_"$n"_"$k"_"$d"_"$m".txt
  fi
done
