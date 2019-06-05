import { module } from 'angular';

import { Registry, SETTINGS, IStage, ExecutionDetailsTasks } from '@spinnaker/core';

import { manifestExecutionDetails } from 'kubernetes/v2/pipelines/stages/ManifestExecutionDetails';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';
import { DeleteManifestStageConfig } from 'kubernetes/v2/pipelines/stages/deleteManifest/DeleteManifestStageConfig';

export const KUBERNETES_DELETE_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.deleteManifestStage';

const STAGE_NAME = 'Delete (Manifest)';
const STAGE_KEY = 'deleteManifest';
module(KUBERNETES_DELETE_MANIFEST_STAGE, []).config(() => {
  // Todo: replace feature flag with proper versioned provider mechanism once available.
  if (SETTINGS.feature.versionedProviders) {
    Registry.pipeline.registerStage({
      label: STAGE_NAME,
      description: 'Destroy a Kubernetes object created from a manifest.',
      key: STAGE_KEY,
      cloudProvider: 'kubernetes',
      component: DeleteManifestStageConfig,
      executionDetailsSections: [manifestExecutionDetails(STAGE_KEY), ExecutionDetailsTasks],
      accountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
      configAccountExtractor: (stage: any): string[] => (stage.account ? [stage.account] : []),
      validators: manifestSelectorValidators(STAGE_NAME),
    });
  }
});
