import { module, IScope } from 'angular';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline';

import { IStage } from 'core/domain';

import { ExecutionDetailsTasks } from '../core';
import { ScriptExecutionDetails } from '../script/ScriptExecutionDetails';
import { AuthenticationService } from 'core/authentication/AuthenticationService';

export const SCRIPT_STAGE = 'spinnaker.core.pipeline.stage.scriptStage';
module(SCRIPT_STAGE, [PIPELINE_CONFIG_PROVIDER])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
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
    });
  })
  .controller('ScriptStageCtrl', ($scope: IScope, stage: IStage) => {
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
