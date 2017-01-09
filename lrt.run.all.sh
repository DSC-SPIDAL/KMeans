#!/bin/bash

#can be true/false to set thread pinning
explicitbind=false
#can be core/socket/none
procbind=core
nodes=32
nodefile=nodes${nodes}.txt
echo $nodefile

#./lrt.run.generic.sh 1 20 $nodefile $nodes 5 g $explicitbind $procbind 1000
pdsh -w j-[035-066] "rm -rf /dev/shm/$USER/*.bin /dev/shm/*.bin"
#./lrt.run.generic.sh 1 20 $nodefile $nodes 5 g $explicitbind $procbind 2000
pdsh -w j-[035-066] "rm -rf /dev/shm/$USER/*.bin /dev/shm/*.bin"
#./lrt.run.generic.sh 1 20 $nodefile $nodes 5 g $explicitbind $procbind 4000
pdsh -w j-[035-066] "rm -rf /dev/shm/$USER/*.bin /dev/shm/*.bin"
./lrt.run.generic.sh 1 20 $nodefile $nodes 5 g $explicitbind $procbind 8000
pdsh -w j-[035-066] "rm -rf /dev/shm/$USER/*.bin /dev/shm/*.bin"
./lrt.run.generic.sh 1 20 $nodefile $nodes 5 g $explicitbind $procbind 16000
pdsh -w j-[035-066] "rm -rf /dev/shm/$USER/*.bin /dev/shm/*.bin"
