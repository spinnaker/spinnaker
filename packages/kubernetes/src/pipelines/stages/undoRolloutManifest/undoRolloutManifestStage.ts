import { module } from 'angular';

import { IStage, Registry } from '@spinnaker/core';

import { KubernetesV2UndoRolloutManifestConfigCtrl } from './undoRolloutManifestConfig.controller';

export const KUBERNETES_UNDO_ROLLOUT_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.undoRolloutManifestStage';

module(KUBERNETES_UNDO_ROLLOUT_MANIFEST_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Undo Rollout (Manifest)',
      description: 'Rollback a manifest a target number of revisions.',
      key: 'undoRolloutManifest',
      cloudProvider: 'kubernetes',
      templateUrl: require('./undoRolloutManifestConfig.html'),
      controller: 'KubernetesV2UndoRolloutManifestConfigCtrl',
      controllerAs: 'ctrl',
      accountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
      configAccountExtractor: (stage: any): string[] => (stage.account ? [stage.account] : []),
      validators: [
        { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
        { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
        { type: 'requiredField', fieldName: 'numRevisionsBack', fieldLabel: 'Number of Revisions' },
        { type: 'manifestSelector' },
      ],
    });
  })
  .controller('KubernetesV2UndoRolloutManifestConfigCtrl', KubernetesV2UndoRolloutManifestConfigCtrl);
