#!/bin/bash
nodes=2
name="$nodes"n
nodefile=nodes.$name.txt

#./lrt.small.run.generic.sh 1 1 $nodefile $nodes 1 g
#./lrt.small.run.generic.sh 1 2 $nodefile $nodes 1 g
./lrt.small.run.generic.sh 1 1 $nodefile $nodes 1 g
#./lrt.small.run.generic.sh 1 2 $nodefile $nodes 1 g
