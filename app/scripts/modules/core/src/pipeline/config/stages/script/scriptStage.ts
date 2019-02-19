import { module, IScope } from 'angular';

import { AuthenticationService } from 'core/authentication/AuthenticationService';
import { IStage } from 'core/domain';
import { Registry } from 'core/registry';

import { ExecutionDetailsTasks } from '../common';
import { ScriptExecutionDetails } from '../script/ScriptExecutionDetails';

export const SCRIPT_STAGE = 'spinnaker.core.pipeline.stage.scriptStage';
module(SCRIPT_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Script',
      description: 'Runs a script',
      defaultTimeoutMs: 1000 * 60 * 60 * 2, // 2 hours
      key: 'script',
      restartable: true,
      controller: 'ScriptStageCtrl',
      controllerAs: 'scriptStageCtrl',
      templateUrl: require('./scriptStage.html'),
      executionDetailsSections: [ScriptExecutionDetails, ExecutionDetailsTasks],
      strategy: true,
      validators: [{ type: 'requiredField', fieldName: 'command' }],
    });
  })
  .controller('ScriptStageCtrl', function($scope: IScope, stage: IStage) {
    $scope.stage = stage;
    $scope.stage.failPipeline = $scope.stage.failPipeline === undefined ? true : $scope.stage.failPipeline;
    $scope.stage.waitForCompletion =
      $scope.stage.waitForCompletion === undefined ? true : $scope.stage.waitForCompletion;

    if (!$scope.stage.user) {
      $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
    }

    $scope.viewState = {
      loading: false,
    };
  });
