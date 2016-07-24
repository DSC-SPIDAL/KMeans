#!/bin/bash
find . -maxdepth 1 -name "*x*.txt" -exec collectoutputtiming.givemegrep.sh {} "Compute" \;
