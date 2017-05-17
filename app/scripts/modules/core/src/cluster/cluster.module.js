'use strict';

import {CLUSTER_POD_COMPONENT} from 'core/cluster/clusterPod.component';
import {ALL_CLUSTERS_GROUPINGS_COMPONENT} from './allClustersGroupings.component';
import {ON_DEMAND_CLUSTER_PICKER_COMPONENT} from './onDemand/onDemandClusterPicker.component';
import './ClusterSearchResultFormatter';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.cluster', [
    require('./allClusters.controller.js'),
    ALL_CLUSTERS_GROUPINGS_COMPONENT,
    CLUSTER_POD_COMPONENT,
    ON_DEMAND_CLUSTER_PICKER_COMPONENT,
  ]);
