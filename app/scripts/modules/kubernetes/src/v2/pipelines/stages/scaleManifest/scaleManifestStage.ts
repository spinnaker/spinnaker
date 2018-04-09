import { module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER, PipelineConfigProvider, SETTINGS } from '@spinnaker/core';
import { KubernetesV2ScaleManifestConfigCtrl } from './scaleManifestConfig.controller';
import { KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM } from '../../../manifest/scale/scaleSettingsForm.component';

export const KUBERNETES_SCALE_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.scaleManifestStage';

module(KUBERNETES_SCALE_MANIFEST_STAGE, [PIPELINE_CONFIG_PROVIDER, KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM])
  .config((pipelineConfigProvider: PipelineConfigProvider) => {
    // Todo: replace feature flag with proper versioned provider mechanism once available.
    if (SETTINGS.feature.versionedProviders) {
      pipelineConfigProvider.registerStage({
        label: 'Scale (Manifest)',
        description: 'Scale a Kubernetes object created from a manifest.',
        key: 'scaleManifest',
        cloudProvider: 'kubernetes',
        templateUrl: require('./scaleManifestConfig.html'),
        controller: 'KubernetesV2ScaleManifestConfigCtrl',
        controllerAs: 'ctrl',
        validators: [
          { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
          { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
          { type: 'requiredField', fieldName: 'name', fieldLabel: 'manifestName' },
        ],
      });
    }
  })
  .controller('KubernetesV2ScaleManifestConfigCtrl', KubernetesV2ScaleManifestConfigCtrl);
