'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.filters', [
])
.directive(require('./anyFieldFilter.js'))
.directive(require('./robotToHuman.js'))
.directive(require('./stageNames.js'));

