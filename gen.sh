#!/bin/bash

cp=target/kmeans-1.0-spidal-lrt-jar-with-dependencies.jar
#n=1000000
n=$1
#d=2
d=100
#k=1000
k=$2
p=$3
base_dir=/N/u/skamburu/projects/KMeans_fork/data/
point_out=$base_dir/$n/$n
center_out=$base_dir/centers/$k

mkdir -p $base_dir/$n
mkdir -p $base_dir/centers

#big endian data
#java -cp $cp org.saliya.ompi.kmeans.DataGenerator -n $n -d $d -k $k -o $HOME/sali/git/github/esaliya/java/KMeans/tmp -b true -t false

#little endian data
echo java -cp $cp org.saliya.ompi.kmeans.DataGenerator -n $n -d $d -k $k -o /home/supun/dev/projects/dsspidal/KMeans/data/$n-$d-$k -b false -t true
java -cp $cp org.saliya.ompi.kmeans.DataGenerator -n $n -d $d -k $k -p $point_out -c $center_out -b false -t false
