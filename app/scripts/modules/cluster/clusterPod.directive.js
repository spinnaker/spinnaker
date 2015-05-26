'use strict';

angular.module('spinnaker.cluster.pod', [
  'spinnaker.serverGroup.sequence.filter',
  'spinnaker.urlBuilder',
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
