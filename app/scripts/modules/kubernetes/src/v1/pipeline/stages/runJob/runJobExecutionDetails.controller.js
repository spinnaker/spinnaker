import { get } from 'lodash';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';

('use strict');

import { module } from 'angular';

export const KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBEXECUTIONDETAILS_CONTROLLER =
  'spinnaker.kubernetes.pipeline.stage.disableCluster.runJobExecutionDetails.controller';
export const name = KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBEXECUTIONDETAILS_CONTROLLER; // for backwards compatibility
module(KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBEXECUTIONDETAILS_CONTROLLER, [UIROUTER_ANGULARJS]).controller(
  'kubernetesRunJobExecutionDetailsCtrl',
  [
    '$scope',
    '$stateParams',
    'executionService',
    'executionDetailsSectionService',
    '$uibModal',
    function($scope, $stateParams, executionService, executionDetailsSectionService, $uibModal) {
      $scope.configSections = ['runJobConfig', 'taskStatus'];
      $scope.executionId = $stateParams.executionId;

      // if the stage is pre-multi-containers
      if ($scope.stage.context.container) {
        $scope.stage.context.containers = [$scope.stage.context.container];
      }

      const initialized = () => {
        $scope.detailsSection = $stateParams.details;
      };

      const initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

      initialize();

      $scope.$on('$stateChangeSuccess', initialize);

      $scope.displayLogs = () => {
        $scope.logs = $scope.stage.context.jobStatus.logs || '';
        $scope.jobName = $scope.stage.context.jobStatus.name || '';
        $scope.loading = $scope.logs === '';
        $uibModal.open({
          templateUrl: require('./runJobLogs.html'),
          scope: $scope,
          size: 'lg',
        });
        if ($scope.logs === '') {
          const getExecutionPromise = executionService.getExecution($scope.executionId);
          getExecutionPromise.finally(() => {
            $scope.loading = false;
          });
          getExecutionPromise.then(execution => {
            const fullStage = execution.stages.find(s => s.id === $scope.stage.id);
            if (fullStage) {
              $scope.logs = get(fullStage, 'context.jobStatus.logs', 'No log output found');
            }
          });
        }
      };
    },
  ],
);
