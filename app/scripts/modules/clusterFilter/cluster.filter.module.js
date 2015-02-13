angular
  .module('deckApp.cluster', [
    'deckApp.instanceList.filter',
    'cluster',
    'clusters.all',
    'cluster.filter.collapse',
    'deckApp.utils.lodash',
    'deckApp.cluster.controller',
    'deckApp.clusterNav.controller',
  ]);
