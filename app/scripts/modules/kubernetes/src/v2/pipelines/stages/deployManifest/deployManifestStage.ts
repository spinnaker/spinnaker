import { module } from 'angular';

import {
  ArtifactReferenceService,
  EXECUTION_ARTIFACT_TAB,
  ExecutionArtifactTab,
  ExecutionDetailsTasks,
  ExpectedArtifactService,
  IStage,
  Registry,
  SETTINGS,
} from '@spinnaker/core';

import { KubernetesV2DeployManifestConfigCtrl } from './deployManifestConfig.controller';
import { MANIFEST_BIND_ARTIFACTS_SELECTOR_REACT } from './ManifestBindArtifactsSelector';
import { MANIFEST_DEPLOYMENT_OPTIONS } from './ManifestDeploymentOptions';
import { DeployStatus } from './react/DeployStatus';

export const KUBERNETES_DEPLOY_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.deployManifestStage';

module(KUBERNETES_DEPLOY_MANIFEST_STAGE, [
  EXECUTION_ARTIFACT_TAB,
  MANIFEST_BIND_ARTIFACTS_SELECTOR_REACT,
  MANIFEST_DEPLOYMENT_OPTIONS,
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
        executionDetailsSections: [DeployStatus, ExecutionDetailsTasks, ExecutionArtifactTab],
        producesArtifacts: true,
        defaultTimeoutMs: 30 * 60 * 1000, // 30 minutes
        validators: [],
        accountExtractor: (stage: IStage): string => (stage.account ? stage.account : ''),
        configAccountExtractor: (stage: any): string[] => (stage.account ? [stage.account] : []),
        artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['manifestArtifactId', 'requiredArtifactIds']),
        artifactRemover: ArtifactReferenceService.removeArtifactFromFields([
          'manifestArtifactId',
          'requiredArtifactIds',
        ]),
      });
    }
  })
  .controller('KubernetesV2DeployManifestConfigCtrl', KubernetesV2DeployManifestConfigCtrl);
