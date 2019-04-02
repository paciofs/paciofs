#!/bin/bash

# requires MultiChain explorer to be installed (use Python 2 environment)
# https://github.com/MultiChain/multichain-explorer

# clean DB from previous run
rm -f ./paciofs.explorer.sqlite

# use the same Python 2 environment
python -m Mce.abe --config ./paciofs.conf --commit-bytes 100000
