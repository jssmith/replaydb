
set grid x y
unset key

set yrange [((num_phases+4)*num_partitions):-1]
set ytics _REPLAY_YTICS_
if (xrange_max eq "*") {
  set xrange [(xrange_min):*]
} else {
  set xrange [(xrange_min):(xrange_max)]
}
set xlabel 'Time Since Start (ms)'
set ylabel 'ReplayDB (partitionId-phaseId)'

if (term_type eq "png") {
  set terminal pngcairo size (output_width),(output_height)
  set output sprintf('%s.png', output_filename)
} else {
  if (term_type eq "wxt") {
    set terminal wxt persist
  } else {
    set terminal x11 persist
  }
}

line_weight = 10

set style arrow 1 nohead filled lw (line_weight) lc rgb "#FF5555"
set style arrow 2 nohead filled lw (line_weight) lc rgb "#33FFFF"
set style arrow 3 nohead filled lw (line_weight) lc rgb "#FF33FF"
set style arrow 4 nohead filled lw (line_weight) lc rgb "#FFFF33"
set style arrow 5 nohead filled lw (line_weight) lc rgb "#5555FF"
set style arrow 6 nohead filled lw (line_weight) lc rgb "#33FF33"
set style arrow 12 nohead filled lw (line_weight) lc rgb "#DD3333FF"

set style line 1 lw 1 dt 1
set style line 2 lw 1 dt 2
set style line 3 lw 1 dt 3
set style line 4 lw 1 dt 4
set style line 5 lw 1 dt 5
set style line 6 lw 1 dt 6

filename(n) = sprintf('/tmp/partition%d.dat', n)
batch_filename(n) = sprintf('/tmp/batches%d.dat', n / batch_boundary_cols)

batch_boundary_phase_cnt = words(batch_boundary_phases)

plot \
  for [i=0:(num_partitions-1)] filename(i) \
    using ($2-start_ts):($1+(num_phases+4)*i):($3-$2):(0.0):5 \
    with vectors arrowstyle variable, \
  for [i=0:(num_partitions-1)] filename(i) \
    using (($2-start_ts)+($3-$2)/2):($1+(num_phases+4)*i):($4==-1 ? "" : $4) with labels, \
  for [i=0:(batch_boundary_cols*batch_boundary_phase_cnt-1)] batch_filename(i) \
    using (column((i%batch_boundary_cols)+2)-start_ts):1 \
    with lines ls (i/batch_boundary_cols+1) lc (i%batch_boundary_cols)

