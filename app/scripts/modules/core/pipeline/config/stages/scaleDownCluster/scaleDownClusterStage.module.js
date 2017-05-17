'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.scaleDownCluster', [
  require('./scaleDownClusterStage.js'),
]);
