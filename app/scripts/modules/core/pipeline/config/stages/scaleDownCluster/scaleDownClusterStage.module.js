'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.scaleDownCluster', [
  require('./scaleDownClusterStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('core/account/account.module.js'),
]);
