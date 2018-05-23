import { module } from 'angular';

import { ArtifactReferenceService, EXECUTION_ARTIFACT_TAB, Registry, SETTINGS } from '@spinnaker/core';

import { KubernetesV2DeployManifestConfigCtrl } from './deployManifestConfig.controller';
import { KUBERNETES_DEPLOY_MANIFEST_DEPLOY_STATUS_MANIFEST_SUMMARY } from './deployStatusManifestSummary';

export const KUBERNETES_DEPLOY_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.deployManifestStage';

module(KUBERNETES_DEPLOY_MANIFEST_STAGE, [
  EXECUTION_ARTIFACT_TAB,
  KUBERNETES_DEPLOY_MANIFEST_DEPLOY_STATUS_MANIFEST_SUMMARY,
])
  .config(() => {
    // Todo: replace feature flag with proper versioned provider mechanism once available.
    if (SETTINGS.feature.versionedProviders) {
      Registry.pipeline.registerStage({
        label: 'Deploy (Manifest)',
        description: 'Deploy a Kubernetes manifest yaml/json file.',
        key: 'deployManifest',
        cloudProvider: 'kubernetes',
        templateUrl: require('./deployManifestConfig.html'),
        controller: 'KubernetesV2DeployManifestConfigCtrl',
        controllerAs: 'ctrl',
        artifactFields: ['manifestArtifactId', 'requiredArtifactIds'],
        executionDetailsUrl: require('./deployManifestExecutionDetails.html'),
        executionConfigSections: ['deployStatus', 'taskStatus', 'artifactStatus'],
        producesArtifacts: true,
        defaultTimeoutMs: 30 * 60 * 1000, // 30 minutes
        validators: [],
      });

      ArtifactReferenceService.registerReference('stage', () => [['manifestArtifactId'], ['requiredArtifactIds']]);
    }
  })
  .controller('KubernetesV2DeployManifestConfigCtrl', KubernetesV2DeployManifestConfigCtrl);
