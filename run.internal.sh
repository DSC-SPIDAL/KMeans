#!/bin/bash

opts=$1
cp=$2
n=$3
d=$4
k=$5
t=$6
c=$7
p=$8
m=$9
b=${10}
T=${11}
explicitbind=${12}
pat=${13}
if [[ $HOSTNAME == "j-002" || $HOSTNAME == "j-003" || $HOSTNAME == "j-005" ]]; then 
  export LD_PRELOAD="/opt/pontus-vision/4.7.0/linux/lib64/libpvtm-agent-preload.so /opt/pontus-vision/4.7.0/linux/libpvtm-agent-ioboost-preload.so"
  #echo $LD_PRELOAD
  java -javaagent:/opt/pontus-vision/4.7.0/linux/pvtm-agent.jar $opts -cp $cp org.saliya.ompi.kmeans.Program -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T -bind $explicitbind 2>&1 | tee fj_"$pat"_"$n"_"$k"_"$d"_"$m".txt
else
  #echo hi
  java $opts -cp $cp org.saliya.ompi.kmeans.Program -n $n -d $d -k $k -t $t -c $c -p $p -m $m -b $b -o out.txt -T $T -bind $explicitbind 2>&1 | tee fj_"$pat"_"$n"_"$k"_"$d"_"$m".txt
fi


