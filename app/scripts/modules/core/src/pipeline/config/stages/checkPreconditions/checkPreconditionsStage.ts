import { IScope, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

export const CHECK_PRECONDITIONS_STAGE = 'spinnaker.pipelines.stage.checkPreconditionsStage';

module(CHECK_PRECONDITIONS_STAGE, [PIPELINE_CONFIG_PROVIDER])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    pipelineConfigProvider.registerStage({
      label: 'Check Preconditions',
      description: 'Checks for preconditions before continuing',
      key: 'checkPreconditions',
      restartable: true,
      controller: 'CheckPreconditionsStageCtrl',
      controllerAs: 'checkPreconditionsStageCtrl',
      templateUrl: require('./checkPreconditionsStage.html'),
      executionDetailsUrl: require('./checkPreconditionsExecutionDetails.html'),
      executionConfigSections: ['checkPreconditions', 'taskStatus'],
      strategy: true,
    });
  })
  .controller('CheckPreconditionsStageCtrl', ($scope: IScope) => {
    $scope.stage.preconditions = $scope.stage.preconditions || [];
  });
