#!/bin/bash
find . -maxdepth 1 -name "*x*.txt" -exec collectoutputtiming.givemegrep.sh {} "Comm" \;
#find . -maxdepth 1 -name "*x*.txt" -exec collectoutputtiming.givemegrep.sh {} "Barrier" \;
