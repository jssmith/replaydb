#!/bin/bash
#
# Locally generate data
# Usage: ./lgendata.sh size_spec num_events num_users split_nums [ keep_only=-1 ]
#  e.g.: ./lgendata.sh 50m 50000000 100000 "1 2 4 8 16 32" 1
#
#        keep_only denotes the denotes the *only* output partition that should
#        actually be saved to disk (-1 for all). should be this host's partition ID
#

ROOT_DIR=/home/ec2-user/data0

if [[ $# -lt 4 ]]; then
  echo "Usage: ./lgendata.sh size_spec num_events num_users split_nums [ batches=1 ] [ keep_only=-1 ] [ partitioned=false ] [ alpha=2.0 ]"
  echo "e.g.: ./lgendata.sh 50m 50000000 100000 \"1 2 4 8 16 32\" 1 1 true 2.0"
  echo "   keep_only denotes the denotes the *only* output partition that should "
  echo "   actually be saved to disk (-1 for all). should be this host's partition ID"
  echo ""
  echo "batches is the number of batches to split the files into - ONLY for spark"
  echo "can't use partitioned = true and batches != 1 at the same time"
  exit
fi

SIZE_SPEC=$1
NUM_EVENTS=$2
NUM_USERS=$3
SPLIT_NUMS=$4
if [ $# -ge 5 ]; then
  BATCHES=$5
else
  BATCHES=1
fi
if [ $# -ge 6 ]; then
  KEEP_ONLY=$6
else
  KEEP_ONLY=-1
fi
if [ $# -ge 7 ]; then
  PARTITIONED=$7
else
  PARTITIONED=false
fi
if [ $PARTITIONED = true ]; then
  DATA_PARTITION_SUFFIX=-part
else
  if [ $BATCHES = 1 ]; then
    DATA_PARTITION_SUFFIX=
  else
    DATA_PARTITION_SUFFIX=-$BATCHES
  fi
fi
if [ $# -ge 8 ]; then
  ALPHA=$8
else
  ALPHA='2.0'
fi

for i in ${SPLIT_NUMS[*]}; do
  rm -rf $ROOT_DIR/events-$SIZE_SPEC-$ALPHA-split-$i-$DATA_PARTITION_SUFFIX
  java -cp /home/ec2-user/replaydb-worker/replaydb-apps-assembly-0.1-SNAPSHOT.jar replaydb.exec.EventGenerator \
    tunable $NUM_USERS $NUM_EVENTS $ROOT_DIR/events-$SIZE_SPEC-$ALPHA-split-$i$DATA_PARTITION_SUFFIX/events.out $i $KEEP_ONLY $PARTITIONED $BATCHES $ALPHA
done
