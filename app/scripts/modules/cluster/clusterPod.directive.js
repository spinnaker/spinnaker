'use strict';

angular.module('deckApp.cluster.pod', [
  'deckApp.serverGroup.sequence.filter',
  'deckApp.urlBuilder',
])
  .directive('clusterPod', function(urlBuilder) {
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
      link: function(scope) {
        scope.permalink = urlBuilder.buildFromMetadata(
          {
            type: 'clusters',
            application: scope.application.name,
            cluster: scope.grouping.heading,
            account: scope.parentHeading,
          }
        );
      }
    };
  });
