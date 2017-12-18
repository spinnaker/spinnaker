import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider } from 'core/pipeline/config/pipelineConfigProvider';

import { ExecutionDetailsTasks } from 'core/pipeline/config/stages/core';
import { FindArtifactFromExecutionCtrl } from 'core/pipeline/config/stages/findArtifactFromExecution/findArtifactFromExecution.controller';
import { SETTINGS } from 'core/config/settings';

export const FIND_ARTIFACT_FROM_EXECUTION_STAGE = 'spinnaker.core.pipeline.stage.findArtifactStage';

module(FIND_ARTIFACT_FROM_EXECUTION_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
]).config((pipelineConfigProvider: PipelineConfigProvider) => {
  if (SETTINGS.feature.artifacts) {
    pipelineConfigProvider.registerStage({
      label: 'Find Artifact From Execution',
      description: 'Find and bind an artifact from another execution',
      key: 'findArtifactFromExecution',
      templateUrl: require('./findArtifactFromExecutionConfig.html'),
      controller: 'findArtifactFromExecutionCtrl',
      controllerAs: 'ctrl',
      executionDetailsSections: [ExecutionDetailsTasks],
      validators: [
        { type: 'requiredField', fieldName: 'pipeline', fieldLabel: 'Pipeline' },
        { type: 'requiredField', fieldName: 'application', fieldLabel: 'Application' },
      ],
    });
  }
}).controller('findArtifactFromExecutionCtrl', FindArtifactFromExecutionCtrl);
