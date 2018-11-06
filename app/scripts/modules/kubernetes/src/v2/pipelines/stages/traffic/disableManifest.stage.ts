import { module } from 'angular';

import { EXECUTION_ARTIFACT_TAB, ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';
import { ManifestTrafficStageConfig } from './ManifestTrafficStageConfig';
import { trafficValidators } from 'kubernetes/v2/pipelines/stages/traffic/validators';

const STAGE_NAME = 'Disable (Manifest)';
export const KUBERNETES_DISABLE_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.disableManifestStage';
module(KUBERNETES_DISABLE_MANIFEST_STAGE, [EXECUTION_ARTIFACT_TAB]).config(() => {
  Registry.pipeline.registerStage({
    label: STAGE_NAME,
    description: 'Disable a Kubernetes manifest.',
    key: 'disableManifest',
    cloudProvider: 'kubernetes',
    component: ManifestTrafficStageConfig,
    executionDetailsSections: [ExecutionDetailsTasks],
    defaultTimeoutMs: 30 * 60 * 1000, // 30 minutes
    accountExtractor: (stage: IStage): string => (stage.account ? stage.account : ''),
    configAccountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
    validators: trafficValidators(STAGE_NAME),
  });
});
