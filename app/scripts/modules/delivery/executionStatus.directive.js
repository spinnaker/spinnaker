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
          var deploymentDetails = _(scope.execution.stages)
            .chain()
            .find(function(stage) {
              return stage.type === 'deploy';
            })
            .get('context')
            .get('deploymentDetails')
            .value();

          return deploymentDetails && deploymentDetails.length ? deploymentDetails[0].jenkins : null;
        }

        scope.execution.buildInfo = findDeployStageList();

      }
    };
  });
