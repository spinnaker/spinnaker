import { module } from 'angular';

import { EXECUTION_ARTIFACT_TAB, ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { ManifestTrafficStageConfig } from './ManifestTrafficStageConfig';
import { trafficValidators } from 'kubernetes/v2/pipelines/stages/traffic/validators';
import { manifestExecutionDetails } from 'kubernetes/v2/pipelines/stages/ManifestExecutionDetails';

const STAGE_NAME = 'Enable (Manifest)';
const STAGE_KEY = 'enableManifest';
export const KUBERNETES_ENABLE_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.enableManifestStage';
module(KUBERNETES_ENABLE_MANIFEST_STAGE, [EXECUTION_ARTIFACT_TAB]).config(() => {
  Registry.pipeline.registerStage({
    label: STAGE_NAME,
    description: 'Enable a Kubernetes manifest.',
    key: STAGE_KEY,
    cloudProvider: 'kubernetes',
    component: ManifestTrafficStageConfig,
    executionDetailsSections: [manifestExecutionDetails(STAGE_KEY), ExecutionDetailsTasks],
    defaultTimeoutMs: 30 * 60 * 1000, // 30 minutes
    accountExtractor: (stage: IStage): string => (stage.account ? stage.account : ''),
    configAccountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
    validators: trafficValidators(STAGE_NAME),
  });
});
