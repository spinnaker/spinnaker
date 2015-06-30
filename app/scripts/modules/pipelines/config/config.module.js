'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config', [
  require('./actions/actions.module.js'),
  require('./graph/pipeline.graph.directive.js'),
  require('./services/services.module.js'),
  require('./triggers/trigger.module.js'),
]);
