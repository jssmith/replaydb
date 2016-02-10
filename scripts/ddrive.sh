#!/bin/bash
#
# Launch the driver program
# Usage: ./ddrive.sh spam_detector size_spec num_partitions [ partitioned=false ]
#  e.g.: ./ddrive.sh replaydb.exec.spam.SpamDetectorStats 50m 4 true
#


if [ $# -lt 3 ]; then
  echo "Usage: ./ddrive.sh spam_detector size_spec num_partitions [ partitioned=false ]"
  echo " e.g.: ./ddrive.sh replaydb.exec.spam.SpamDetectorStats 50m 4 true"
  exit
fi

SPAM_DETECTOR=$1
SIZE_SPEC=$2
NUM=$3
if [ $# -ge 4 ]; then
  PARTITIONED=$4
else
  PARTITIONED=false
fi

DATA_ROOT=/home/ec2-user/data0

echo "Driving $SPAM_DETECTOR with $NUM partitions"

time java -Xmx2000m \
  -cp /home/ec2-user/replaydb/apps/target/scala-2.11/replaydb-apps-assembly-0.1-SNAPSHOT.jar \
  replaydb.exec.spam.DistributedSpamDetector $SPAM_DETECTOR \
  $DATA_ROOT/events-$SIZE_SPEC-split-$NUM/events.out \
  $NUM 10000 /home/ec2-user/conf/latesthosts.txt $PARTITIONED

