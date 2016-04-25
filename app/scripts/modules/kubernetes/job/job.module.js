'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.job.kubernetes', [
  require('./transformer.js'),
]);
