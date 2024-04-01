#!/bin/bash
# run TransferBurst n times
# Usage: ./demo_test.sh <n>
# n: number of times to run TransferBurst

PACKAGES="base exceptions solution demo"

for package in $PACKAGES
do
  javac cp2023/"$package"/*.java
done

for (( i=1; i<=$1; i++ ))
do
    echo "Run $i"
    java cp2023.demo.TransferBurst
done

for package in $PACKAGES
do
  rm cp2023/"$package"/*.class
done