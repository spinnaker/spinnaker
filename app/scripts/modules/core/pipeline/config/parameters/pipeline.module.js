'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.parameters', [
  require('./parameter.js'),
  require('./parameters.directive.js'),
]);
