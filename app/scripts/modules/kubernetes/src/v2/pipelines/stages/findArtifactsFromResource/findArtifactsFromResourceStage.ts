import { module } from 'angular';

import { Registry, SETTINGS, ExecutionDetailsTasks, ExecutionArtifactTab } from '@spinnaker/core';

import { KubernetesV2FindArtifactsFromResourceConfigCtrl } from './findArtifactsFromResourceConfig.controller';
import { KUBERNETES_MANIFEST_SELECTOR } from '../../../manifest/selector/selector.component';

export const KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE =
  'spinnaker.kubernetes.v2.pipeline.stage.findArtifactsFromResource';

module(KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE, [KUBERNETES_MANIFEST_SELECTOR])
  .config(() => {
    // Todo: replace feature flag with proper versioned provider mechanism once available.
    if (SETTINGS.feature.artifacts) {
      Registry.pipeline.registerStage({
        label: 'Find Artifacts From Resource (Manifest)',
        description: 'Finds artifacts from a Kubernetes resource.',
        key: 'findArtifactsFromResource',
        cloudProvider: 'kubernetes',
        templateUrl: require('./findArtifactsFromResourceConfig.html'),
        controller: 'KubernetesV2FindArtifactsFromResourceConfigCtrl',
        controllerAs: 'ctrl',
        executionDetailsSections: [ExecutionDetailsTasks, ExecutionArtifactTab],
        producesArtifacts: true,
        validators: [
          { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
          { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
          { type: 'manifestSelector' },
        ],
      });
    }
  })
  .controller('KubernetesV2FindArtifactsFromResourceConfigCtrl', KubernetesV2FindArtifactsFromResourceConfigCtrl);
