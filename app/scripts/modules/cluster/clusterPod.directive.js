'use strict';

angular.module('deckApp.cluster.pod', [
  'deckApp.serverGroup.sequence.filter',
])
  .directive('clusterPod', function() {
    return {
      restrict: 'E',
      scope: {
        grouping: '=',
        displayOptions: '=',
        sortFilter: '=',
        application: '=',
        parentHeading: '=',
      },
      templateUrl: 'scripts/modules/cluster/clusterPod.html',
    };
  });
