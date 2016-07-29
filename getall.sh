#!/bin/bash
dir=$(dirname "$0")
$dir/get_total_timing.sh
$dir/get_comp_timing.sh
$dir/get_mpi_timing.sh
