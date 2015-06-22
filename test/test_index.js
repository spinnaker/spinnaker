'use strict';

var testsContext = require.context("../app/scripts/", true, /\.spec\.js$/);
testsContext.keys().forEach(testsContext);