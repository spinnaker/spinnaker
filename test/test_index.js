'use strict';

var testsContext = require.context('../app/scripts/', true, /\.spec\.(js|ts)$/);
testsContext.keys().forEach(testsContext);
