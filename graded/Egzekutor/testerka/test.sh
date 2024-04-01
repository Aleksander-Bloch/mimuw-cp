#!/bin/bash

PROG=${1}

for f in ./programs/*.cc 
do
    echo $f
    name=$(basename -- $f)
    g++ $f -o ${name%.cc}
done

for t in ./tests/*.in
do 
    name=$(basename -- $t)
    echo $name
    ./$PROG <$t >${name%.in}.out
done
