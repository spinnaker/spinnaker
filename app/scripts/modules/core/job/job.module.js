'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.job', [
    require('./job.transformer.js'),
    require('./job.directive.js'),
  ]);
