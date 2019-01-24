'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.parameters', [
  require('./parameter').name,
  require('./parameters.directive').name,
]);
