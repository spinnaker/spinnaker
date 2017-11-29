import { module } from 'angular';

import {
  PIPELINE_CONFIG_PROVIDER,
  PipelineConfigProvider,
  SETTINGS
} from '@spinnaker/core';

import { KubernetesV2DeployManifestConfigCtrl } from './deployManifestConfig.controller';
import {
  KUBERNETES_MANIFEST_COMMAND_BUILDER
} from '../../../manifest/manifestCommandBuilder.service';

export const KUBERNETES_DEPLOY_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.deployManifestStage';

module(KUBERNETES_DEPLOY_MANIFEST_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
  KUBERNETES_MANIFEST_COMMAND_BUILDER,
]).config(function(pipelineConfigProvider: PipelineConfigProvider) {

  // Todo: replace feature flag with proper versioned provider mechanism once available.
  if (SETTINGS.feature.versionedProviders) {
    pipelineConfigProvider.registerStage({
      label: 'Deploy Manifest',
      description: 'Deploy a Kubernetes manifest yaml/json file.',
      key: 'deployManifest',
      cloudProvider: 'kubernetes',
      templateUrl: require('./deployManifestConfig.html'),
      controller: 'KubernetesV2DeployManifestConfigCtrl',
      controllerAs: 'ctrl',
      validators: [
        { type: 'requiredField', fieldName: 'moniker.cluster', fieldLabel: 'Cluster' },
        { type: 'requiredField', fieldName: 'manifestText', fieldLabel: 'Manifest' }
      ],
    });
  }
}).controller('KubernetesV2DeployManifestConfigCtrl', KubernetesV2DeployManifestConfigCtrl);
