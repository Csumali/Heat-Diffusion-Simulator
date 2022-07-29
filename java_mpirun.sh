#!/bin/sh
# $1: #cpus
# $2: your java class
# $3: arguments[0]
# $4: arguments[1]
mpirun -n $1 java $2 $3 $3 $4 $5 $6
