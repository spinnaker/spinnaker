'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.cluster', [
    require('./allClusters.controller.js'),
    require('./clusterPod.directive.js'),
  ]);
