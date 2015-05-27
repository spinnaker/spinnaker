angular
  .module('spinnaker.cluster', [
    'spinnaker.instanceList.filter',
    'cluster',
    'clusters.all',
    'cluster.filter.collapse',
    'spinnaker.utils.lodash',
  ]);
