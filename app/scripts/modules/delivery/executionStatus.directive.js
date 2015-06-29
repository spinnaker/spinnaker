'use strict';

angular.module('spinnaker.delivery.executionStatus.directive', [])
  .directive('executionStatus', function() {
    return {
      restrict: 'E',
      scope: {
        execution: '=',
        filter: '=',
      },
      templateUrl: 'scripts/modules/delivery/executionStatus.html',
      controller: 'executionStatus as ctrl',
      link: function(scope) {

        function findDeployStageList() {
          return _(scope.execution.stages)
            .chain()
            .find(function(stage) {
              return stage.type === 'deploy';
            })
            .get('context')
            .get('deploymentDetails')
            .first()
            .get('jenkins')
            .value();
        }

        scope.execution.buildInfo = findDeployStageList();

      }
    };
  });
