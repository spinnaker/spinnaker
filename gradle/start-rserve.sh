#!/bin/sh

#
# This script is intended to start Rserve on travis-ci, which currently uses
# Ubuntu trusty.  The r-cran-rserve package has a bug where it cannot be used
# directly, but we can start it in other ways, which we will do below.
#
# See https://bugs.launchpad.net/ubuntu/+source/rserve/+bug/1325325 for
# details on the bug.
#

R --no-save --no-restore --quiet << __EOF__
library(Rserve)
Rserve(args="--RS-conf gradle/Rserve.conf --quiet --no-restore --no-save")
q()
__EOF__

