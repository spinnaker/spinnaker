import { module } from 'angular';

import {
  PIPELINE_CONFIG_PROVIDER,
  PipelineConfigProvider,
  SETTINGS
} from '@spinnaker/core';

import { KubernetesV2FindArtifactsFromResourceConfigCtrl } from './findArtifactsFromResourceConfig.controller';
import { KUBERNETES_MANIFEST_SELECTOR } from '../../../manifest/selector/selector.component';

export const KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.findArtifactsFromResource';

module(KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
  KUBERNETES_MANIFEST_SELECTOR
]).config((pipelineConfigProvider: PipelineConfigProvider) => {
    // Todo: replace feature flag with proper versioned provider mechanism once available.
    if (SETTINGS.feature.artifacts) {
      pipelineConfigProvider.registerStage({
        label: 'Find artifacts from resource (Manifest)',
        description: 'Finds artifacts from a Kubernetes resource.',
        key: 'findArtifactsFromResource',
        cloudProvider: 'kubernetes',
        templateUrl: require('./findArtifactsFromResourceConfig.html'),
        controller: 'KubernetesV2FindArtifactsFromResourceConfigCtrl',
        controllerAs: 'ctrl',
        validators: [
          { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
          { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
          { type: 'requiredField', fieldName: 'name', fieldLabel: 'name' },
        ],
      });
    }
  }).controller('KubernetesV2FindArtifactsFromResourceConfigCtrl', KubernetesV2FindArtifactsFromResourceConfigCtrl);
