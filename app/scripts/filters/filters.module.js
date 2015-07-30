'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.filters', [
])
.filter('anyFieldFilter', require('./anyFieldFilter.js'))
.filter('robotToHuman', require('./robotToHuman.js'))
.filter('stageNames', require('./stageNames.js'))
.name;

