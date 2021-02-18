import { module } from 'angular';

import { ArtifactReferenceService, ExpectedArtifactService, IStage, Registry } from '@spinnaker/core';

import { DeployExecutionDetails } from './deployCloudFormationExecutionDetails';
import { DeployCloudFormationStackConfigController } from './deployCloudFormationStackConfig.controller';
import { EvaluateCloudFormationChangeSetExecutionDetails } from './evaluateCloudFormationChangeSetExecutionDetails';
import { EvaluateCloudFormationChangeSetExecutionLabel } from './evaluateCloudFormationChangeSetExecutionLabel';
import { EvaluateCloudFormationChangeSetExecutionMarkerIcon } from './evaluateCloudFormationChangeSetExecutionMarkerIcon';

export const DEPLOY_CLOUDFORMATION_STACK_STAGE = 'spinnaker.amazon.pipeline.stages.deployCloudFormationStage';

module(DEPLOY_CLOUDFORMATION_STACK_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Deploy (CloudFormation Stack)',
      description: 'Deploy a CloudFormation Stack',
      key: 'deployCloudFormation',
      cloudProvider: 'aws',
      templateUrl: require('./deployCloudFormationStackConfig.html'),
      controller: 'DeployCloudFormationStackConfigController',
      controllerAs: 'ctrl',
      useCustomTooltip: true,
      executionDetailsSections: [DeployExecutionDetails, EvaluateCloudFormationChangeSetExecutionDetails],
      executionLabelComponent: EvaluateCloudFormationChangeSetExecutionLabel,
      producesArtifacts: true,
      supportsCustomTimeout: true,
      validators: [],
      markerIcon: EvaluateCloudFormationChangeSetExecutionMarkerIcon,
      accountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
      configAccountExtractor: (stage: any): string[] => (stage.account ? [stage.account] : []),
      artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['stackArtifactId', 'requiredArtifactIds']),
      artifactRemover: ArtifactReferenceService.removeArtifactFromFields(['stackArtifactId', 'requiredArtifactIds']),
    });
  })
  .controller('DeployCloudFormationStackConfigController', DeployCloudFormationStackConfigController);
