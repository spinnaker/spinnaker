import { module } from 'angular';

import { ExecutionArtifactTab, ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import { KubernetesV2FindArtifactsFromResourceConfigCtrl } from './findArtifactsFromResourceConfig.controller';
import { KUBERNETES_MANIFEST_SELECTOR } from '../../../manifest/selector/selector.component';
import { manifestSelectorValidators } from '../validators/manifestSelectorValidators';

export const KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE =
  'spinnaker.kubernetes.v2.pipeline.stage.findArtifactsFromResource';

const STAGE_NAME = 'Find Artifacts From Resource (Manifest)';
module(KUBERNETES_FIND_ARTIFACTS_FROM_RESOURCE_STAGE, [KUBERNETES_MANIFEST_SELECTOR])
  .config(() => {
    Registry.pipeline.registerStage({
      label: STAGE_NAME,
      description: 'Finds artifacts from a Kubernetes resource.',
      key: 'findArtifactsFromResource',
      cloudProvider: 'kubernetes',
      templateUrl: require('./findArtifactsFromResourceConfig.html'),
      controller: 'KubernetesV2FindArtifactsFromResourceConfigCtrl',
      controllerAs: 'ctrl',
      executionDetailsSections: [ExecutionDetailsTasks, ExecutionArtifactTab],
      producesArtifacts: true,
      validators: manifestSelectorValidators(STAGE_NAME),
    });
  })
  .controller('KubernetesV2FindArtifactsFromResourceConfigCtrl', KubernetesV2FindArtifactsFromResourceConfigCtrl);
