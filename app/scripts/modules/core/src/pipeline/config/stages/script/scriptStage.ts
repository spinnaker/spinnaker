import { module, IScope } from 'angular';
import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline';

import { IStage } from 'core/domain';

import { ExecutionDetailsTasks } from '../core';
import { ScriptExecutionDetails } from '../script/ScriptExecutionDetails';
import { AUTHENTICATION_SERVICE, AuthenticationService } from 'core/authentication/authentication.service';

export const SCRIPT_STAGE = 'spinnaker.core.pipeline.stage.scriptStage';
module(SCRIPT_STAGE, [
  AUTHENTICATION_SERVICE,
  PIPELINE_CONFIG_PROVIDER
])
    .config((pipelineConfigProvider: PipelineConfigProvider) => {
      pipelineConfigProvider.registerStage({
        label: 'Script',
        description: 'Runs a script',
        key: 'script',
        restartable: true,
        controller: 'ScriptStageCtrl',
        controllerAs: 'scriptStageCtrl',
        templateUrl: require('./scriptStage.html'),
        executionDetailsSections: [ScriptExecutionDetails, ExecutionDetailsTasks],
        strategy: true,
      });
    })
    .controller('ScriptStageCtrl', ($scope: IScope, stage: IStage, authenticationService: AuthenticationService) => {
      $scope.stage = stage;
      $scope.stage.failPipeline = ($scope.stage.failPipeline === undefined ? true : $scope.stage.failPipeline);
      $scope.stage.waitForCompletion = ($scope.stage.waitForCompletion === undefined ? true : $scope.stage.waitForCompletion);

      if (!$scope.stage.user) {
        $scope.stage.user = authenticationService.getAuthenticatedUser().name;
      }

      $scope.viewState = {
        loading: false
      };
    });
