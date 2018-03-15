# Kayenta
[WIP] Automated Canary Analysis

[![Build Status](https://api.travis-ci.com/spinnaker/kayenta.svg?token=3dcx5xdA8twyS9T3VLnX&branch=master)](https://travis-ci.com/spinnaker/kayenta)

## Kayenta Judge Setup
The Kayenta Judge currently uses R to perform the Mann-Whitney statistical test; Kayenta uses RServe to interface with R.

### Installing RServe
For linux, to install the packages on Ubuntu:
```
apt-get install r-base r-cran-rserve
```

### Running RServe
The following assumes you are in the root of the Kayenta repo:
```
R CMD Rserve --RS-conf gradle/Rserve.conf --no-save
```

or alternatively (especially when running on trusty):

```
./gradle/start-rserve.sh
```
