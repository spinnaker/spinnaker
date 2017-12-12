import { module } from 'angular';

import {
  PIPELINE_CONFIG_PROVIDER,
  PipelineConfigProvider,
  SETTINGS
} from '@spinnaker/core';
import { KubernetesV2DeleteManifestConfigCtrl } from './deleteManifestConfig.controller';
import { KUBERNETES_DELETE_MANIFEST_OPTIONS_FORM } from '../../../manifest/delete/deleteOptionsForm.component';
import { KUBERNETES_MANIFEST_SELECTOR } from '../../../manifest/selector/selector.component';

export const KUBERNETES_DELETE_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.deleteManifestStage';

module(KUBERNETES_DELETE_MANIFEST_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
  KUBERNETES_DELETE_MANIFEST_OPTIONS_FORM,
  KUBERNETES_MANIFEST_SELECTOR,
]).config(function(pipelineConfigProvider: PipelineConfigProvider) {

  // Todo: replace feature flag with proper versioned provider mechanism once available.
  if (SETTINGS.feature.versionedProviders) {
    pipelineConfigProvider.registerStage({
      label: 'Delete (Manifest)',
      description: 'Destroy a Kubernetes object created from a manifest.',
      key: 'deleteManifest',
      cloudProvider: 'kubernetes',
      templateUrl: require('./deleteManifestConfig.html'),
      controller: 'KubernetesV2DeleteManifestConfigCtrl',
      controllerAs: 'ctrl',
      validators: [
        { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
        { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
        { type: 'requiredField', fieldName: 'kinds', fieldLabel: 'Kinds' },
      ],
    });
  }
}).controller('KubernetesV2DeleteManifestConfigCtrl', KubernetesV2DeleteManifestConfigCtrl);
