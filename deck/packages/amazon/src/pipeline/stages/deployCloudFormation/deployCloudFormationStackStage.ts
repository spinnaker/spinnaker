import type { IStage } from '@spinnaker/core';
import { ArtifactReferenceService, ExpectedArtifactService, Registry } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { DeployExecutionDetails } from './deployCloudFormationExecutionDetails';
import { EvaluateCloudFormationChangeSetExecutionDetails } from './evaluateCloudFormationChangeSetExecutionDetails';
import { EvaluateCloudFormationChangeSetExecutionLabel } from './evaluateCloudFormationChangeSetExecutionLabel';
import { EvaluateCloudFormationChangeSetExecutionMarkerIcon } from './evaluateCloudFormationChangeSetExecutionMarkerIcon';

export function registerDeployCloudFormationStackStage(): void {
  Registry.pipeline.registerStage({
    label: 'Deploy (CloudFormation Stack)',
    description: 'Deploy a CloudFormation Stack',
    key: 'deployCloudFormation',
    cloudProvider: 'aws',
    component: AmazonStageConfig,
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
}
