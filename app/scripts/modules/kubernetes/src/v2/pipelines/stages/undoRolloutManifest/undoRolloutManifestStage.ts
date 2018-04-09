import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider, SETTINGS } from '@spinnaker/core';
import { KubernetesV2UndoRolloutManifestConfigCtrl } from './undoRolloutManifestConfig.controller';

export const KUBERNETES_UNDO_ROLLOUT_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.undoRolloutManifestStage';

module(KUBERNETES_UNDO_ROLLOUT_MANIFEST_STAGE, [PIPELINE_CONFIG_PROVIDER])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    // Todo: replace feature flag with proper versioned provider mechanism once available.
    if (SETTINGS.feature.versionedProviders) {
      pipelineConfigProvider.registerStage({
        label: 'Undo Rollout (Manifest)',
        description: 'Rollback a manifest a target number of revisions.',
        key: 'undoRolloutManifest',
        cloudProvider: 'kubernetes',
        templateUrl: require('./undoRolloutManifestConfig.html'),
        controller: 'KubernetesV2UndoRolloutManifestConfigCtrl',
        controllerAs: 'ctrl',
        validators: [
          { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
          { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
          { type: 'requiredField', fieldName: 'kind', fieldLabel: 'Kind' },
          { type: 'requiredField', fieldName: 'name', fieldLabel: 'Manifest Name' },
          { type: 'requiredField', fieldName: 'numRevisionsBack', fieldLabel: 'Number of Revisions' },
        ],
      });
    }
  })
  .controller('KubernetesV2UndoRolloutManifestConfigCtrl', KubernetesV2UndoRolloutManifestConfigCtrl);
