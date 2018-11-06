import { module } from 'angular';

import { Registry, SETTINGS, IStage } from '@spinnaker/core';
import { KubernetesV2ScaleManifestConfigCtrl } from './scaleManifestConfig.controller';
import { KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM } from '../../../manifest/scale/scaleSettingsForm.component';

export const KUBERNETES_SCALE_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.scaleManifestStage';

module(KUBERNETES_SCALE_MANIFEST_STAGE, [KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM])
  .config(() => {
    // Todo: replace feature flag with proper versioned provider mechanism once available.
    if (SETTINGS.feature.versionedProviders) {
      Registry.pipeline.registerStage({
        label: 'Scale (Manifest)',
        description: 'Scale a Kubernetes object created from a manifest.',
        key: 'scaleManifest',
        cloudProvider: 'kubernetes',
        templateUrl: require('./scaleManifestConfig.html'),
        controller: 'KubernetesV2ScaleManifestConfigCtrl',
        controllerAs: 'ctrl',
        accountExtractor: (stage: IStage): string => (stage.account ? stage.account : ''),
        configAccountExtractor: (stage: any): string[] => (stage.account ? [stage.account] : []),
        validators: [
          { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
          { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
          { type: 'manifestSelector' },
        ],
      });
    }
  })
  .controller('KubernetesV2ScaleManifestConfigCtrl', KubernetesV2ScaleManifestConfigCtrl);
