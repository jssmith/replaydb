#!/bin/bash
#
# Launch the driver program
# Usage: ./ddrive.sh spam_detector size_spec num_partitions [ waitAtBatchBoundary=true ] [ partitioned=false ]
#  e.g.: ./ddrive.sh replaydb.exec.spam.SpamDetectorStats 50m 4 true true
#


if [ $# -lt 3 ]; then
  echo "Usage: ./ddrive.sh spam_detector size_spec num_partitions [ batch_size=10000 ] [ waitAtBatchBoundary=true ] [ partitioned=false ]"
  echo " e.g.: ./ddrive.sh replaydb.exec.spam.SpamDetectorStats 50m 4 10000 true true"
  exit
fi

SPAM_DETECTOR=$1
SIZE_SPEC=$2
NUM=$3
if [ $# -ge 4 ]; then
  BATCH_SIZE=$4
else
  BATCH_SIZE=10000
fi
if [ $# -ge 5 ]; then
  WAIT_AT_BOUNDARY=$5
else
  WAIT_AT_BOUNDARY=true
fi
if [ $# -ge 6 ]; then
  PARTITIONED=$6
else
  PARTITIONED=false
fi

DATA_ROOT=/home/ec2-user/data0
if [ $PARTITIONED = true ]; then
  DATA_PARTITION_SUFFIX=-part
else
  DATA_PARTITION_SUFFIX=
fi

echo "Driving $SPAM_DETECTOR with $NUM partitions"

time java -Xmx2000m \
  -cp /home/ec2-user/replaydb/apps/target/scala-2.11/replaydb-apps-assembly-0.1-SNAPSHOT.jar \
  replaydb.exec.spam.DistributedSpamDetector $SPAM_DETECTOR \
  $DATA_ROOT/events-$SIZE_SPEC-split-$NUM$DATA_PARTITION_SUFFIX/events.out \
  $NUM $BATCH_SIZE /home/ec2-user/conf/latesthosts.txt $WAIT_AT_BOUNDARY $PARTITIONED

