import { module } from 'angular';

import { EXECUTION_ARTIFACT_TAB, ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';

import { manifestExecutionDetails } from '../ManifestExecutionDetails';
import { ManifestTrafficStageConfig } from './ManifestTrafficStageConfig';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';

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
    supportsCustomTimeout: true,
    accountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
    configAccountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
    validators: manifestSelectorValidators(STAGE_NAME),
  });
});
