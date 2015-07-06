'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.parameters', [
  require('../../pipelines.module.js'),
  require('./parameter.js'),
  require('./parameters.directive.js'),
]).name;
