'use strict';

import {SHRINK_CLUSTER_EXECUTION_DETAILS_CTRL} from './templates/shrinkClusterExecutionDetails.controller';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.shrinkCluster', [
  require('./shrinkClusterStage.js'),
  SHRINK_CLUSTER_EXECUTION_DETAILS_CTRL,
]);
