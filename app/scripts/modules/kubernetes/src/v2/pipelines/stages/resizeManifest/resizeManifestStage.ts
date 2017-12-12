import { module } from 'angular';

import {
  PIPELINE_CONFIG_PROVIDER,
  PipelineConfigProvider,
  SETTINGS
} from '@spinnaker/core';
import { KubernetesV2ResizeManifestConfigCtrl } from './resizeManifestConfig.controller';
import { KUBERNETES_MANIFEST_SELECTOR } from '../../../manifest/selector/selector.component';
import { KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM } from '../../../manifest/scale/scaleSettingsForm.component';

export const KUBERNETES_RESIZE_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.resizeManifestStage';

module(KUBERNETES_RESIZE_MANIFEST_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
  KUBERNETES_MANIFEST_SELECTOR,
  KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM,
]).config(function(pipelineConfigProvider: PipelineConfigProvider) {

  // Todo: replace feature flag with proper versioned provider mechanism once available.
  if (SETTINGS.feature.versionedProviders) {
    pipelineConfigProvider.registerStage({
      label: 'Resize (Manifest)',
      description: 'Resize a Kubernetes object created from a manifest.',
      key: 'scaleManifest',
      cloudProvider: 'kubernetes',
      templateUrl: require('./resizeManifestConfig.html'),
      controller: 'KubernetesV2ResizeManifestConfigCtrl',
      controllerAs: 'ctrl',
      validators: [
        { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
        { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
        { type: 'requiredField', fieldName: 'kinds', fieldLabel: 'Kinds' },
      ],
    });
  }
}).controller('KubernetesV2ResizeManifestConfigCtrl', KubernetesV2ResizeManifestConfigCtrl);
