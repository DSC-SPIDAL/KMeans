#!/bin/bash

cp=target/kmeans-1.0-spidal-lrt-jar-with-dependencies.jar

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

wd=/N/u/skamburu/projects/KMeans_fork
points=10000000
centers=$9

exp="$points"_"$centers"
p=/scratch/skamburu/data/kmeans/points/$points
c=/scratch/skamburu/data/kmeans/centers/$9
n=$points
d=100
k=$centers
m=10
t=0.00001
b=false
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
echo $pat
#opts="-XX:+UseG1GC -Xms256m -Xmx"$5""$6""
opts="-XX:+UseSerialGC -Xms256m -Xmx"$5""$6""

explicitbind=$7
procbind=$8

reportmpibindings=--report-bindings
#reportmpibindings=
btl="--mca btl tcp,sm,self --mca btl_tcp_if_include eth1"
if [ $procbind = "core" ]; then
    # with IB and bound to corresponding PEs
    mpirun $btl --report-bindings --map-by ppr:$ppn:node:PE=$pe --bind-to core -hostfile $hostfile -np $(($nodes*$ppn)) java $opts -cp $cp org.saliya.ompi.kmeans.ProgramLRT -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T -bind $explicitbind -partition false 2>&1 | tee lrt_"$pat"_"$n"_"$k"_"$d"_"$m".txt
elif [ $procbind = "socket" ]; then
   # with IB and bound to socket
   mpirun $btl --report-bindings --map-by ppr:$ppn:node --bind-to socket -hostfile $hostfile -np $(($nodes*$ppn)) java $opts -cp $cp org.saliya.ompi.kmeans.ProgramLRT -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T -bind $explicitbind -partition false 2>&1 | tee lrt_"$pat"_"$n"_"$k"_"$d"_"$m".txt
else
    # with IB but bound to none
    #with pontus pvtm
    #mpirun $btl --report-bindings --map-by ppr:$ppn:node --bind-to none -hostfile $hostfile -np $(($nodes*$ppn)) ./lrt.run.internal.sh "$opts" "$cp" $n $d $k $t $c $p $m $b $T $explicitbind $pat
    mpirun $btl --report-bindings --map-by ppr:$ppn:node --bind-to none -hostfile $hostfile -np $(($nodes*$ppn)) java $opts -cp $cp org.saliya.ompi.kmeans.ProgramLRT -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T -bind $explicitbind -partition false 2>&1 | tee lrt_"$pat"_"$n"_"$k"_"$d"_"$m".txt
fi

