'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.filters', [
])
.filter('anyFieldFilter', require('./anyField.filter.js'))
.filter('robotToHuman', require('./robotToHuman.filter.js'))
.filter('stageNames', require('./stageNames.filter.js'))
.name;

