import { IScope, module } from 'angular';

import { Registry } from 'core/registry';

import { CheckPreconditionsExecutionDetails } from './CheckPreconditionsExecutionDetails';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';

export const CHECK_PRECONDITIONS_STAGE = 'spinnaker.pipelines.stage.checkPreconditionsStage';

module(CHECK_PRECONDITIONS_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Check Preconditions',
      description: 'Checks for preconditions before continuing',
      key: 'checkPreconditions',
      restartable: true,
      controller: 'CheckPreconditionsStageCtrl',
      controllerAs: 'checkPreconditionsStageCtrl',
      templateUrl: require('./checkPreconditionsStage.html'),
      executionDetailsSections: [CheckPreconditionsExecutionDetails, ExecutionDetailsTasks],
      strategy: true,
    });
  })
  .controller('CheckPreconditionsStageCtrl', ['$scope', function($scope: IScope) {
    $scope.stage.preconditions = $scope.stage.preconditions || [];
  }]);
