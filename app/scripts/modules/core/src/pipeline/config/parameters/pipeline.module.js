'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.parameters', [
  require('./parameter.js').name,
  require('./parameters.directive.js').name,
]);
