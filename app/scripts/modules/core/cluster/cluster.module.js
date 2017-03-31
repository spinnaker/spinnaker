'use strict';

import {CLUSTER_POD_COMPONENT} from 'core/cluster/clusterPod.component';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.cluster', [
    require('./allClusters.controller.js'),
    CLUSTER_POD_COMPONENT,
  ]);
