#!/usr/bin/python2.7

import os
import sys
import subprocess
import heapq

#
# Script to generate a plot of phase progress (event time) vs. real time
# Currently just displays the output, but gnuplot can also generate e.g. svg / eps
# Usage: ./generate_completion_plot.py path_to_part0.perf path_to_part1.perf ...
#

if len(sys.argv) < 2:
    print 'Usage: ./generate_completion_plot.py path_to_part0.perf path_to_part1.perf ... '
    quit()

start_ts = sys.maxint
num_partitions = 0

event_start_time = None

def rgb(hex):
    r, g, b = map(lambda n: int(n, 16), [hex[0:2], hex[2:4], hex[4:6]])
    return 65536 * r + 256 * g + b
colors = map(rgb, ("FF5555", "33FFFFF", "FF33FF", "FF0000", "5555FF", "33FF33"))

for fname in sys.argv[1:]:
    with open(fname, 'r') as f:
        lines = f.readlines()

    lines = [l[:-1].split(' ')[6:-1] for l in lines if 'PROGRESS' in l]

    # lines = (partitionId, phaseId, batchEndTs(millis), currentTime(nanos))

    partition_num = int(lines[0][0])
    num_partitions = max(num_partitions, partition_num + 1)
    num_phases = max(map(lambda l: int(l[1]), lines)) + 1

    start_ts = min(start_ts, min(map(lambda l: int(l[3]), lines)))
    if event_start_time is None:
        two_smallest = heapq.nsmallest(2, set(map(lambda l: int(l[2]), lines)))
        event_start_time = two_smallest[0] - (two_smallest[1] - two_smallest[0])

    phases = list()
    for i in xrange(0, num_phases):
        phases.append(list())

    for line in lines:
        phases[int(line[1])].append((int(line[3]), int(line[2]) - event_start_time))

    for phase_num, phase in enumerate(phases):
        with open('/tmp/phase{}-{}.dat'.format(partition_num, phase_num), 'w') as f:
            for realtime, eventtime in phase:
                f.write('{} {} {}\n'.format(realtime, eventtime, colors[phase_num]))  # phase_num + 1))

subprocess.call(['gnuplot', '-e', 'start_ts={}; num_phases={}; num_partitions={}'.format(start_ts, num_phases, num_partitions), 'completionplot.gnu'])

# for part_idx in xrange(0, num_partitions):
#     for phase_idx in xrange(0, num_phases):
#         os.unlink('/tmp/phase{}-{}.dat'.format(part_idx, phase_idx))
