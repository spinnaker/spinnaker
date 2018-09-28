import { module } from 'angular';

import {
  ArtifactReferenceService,
  ExecutionArtifactTab,
  ExecutionDetailsTasks,
  ExpectedArtifactService,
  Registry,
  SETTINGS,
} from '@spinnaker/core';
import { KubernetesV2PatchManifestConfigCtrl } from '../patchManifest/patchManifestConfig.controller';
import { KUBERNETES_PATCH_MANIFEST_OPTIONS_FORM } from './patchOptionsForm.component';
import { KUBERNETES_MANIFEST_SELECTOR } from 'kubernetes/v2/manifest/selector/selector.component';
import { DeployStatus } from '../deployManifest/react/DeployStatus';

export const KUBERNETES_PATCH_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.patchManifestStage';

export class PatchStatus extends DeployStatus {
  public static title = 'PatchStatus';
}

module(KUBERNETES_PATCH_MANIFEST_STAGE, [KUBERNETES_PATCH_MANIFEST_OPTIONS_FORM, KUBERNETES_MANIFEST_SELECTOR])
  .config(() => {
    if (SETTINGS.feature.versionedProviders) {
      Registry.pipeline.registerStage({
        label: 'Patch (Manifest)',
        description: 'Patch a Kubernetes object in place.',
        key: 'patchManifest',
        cloudProvider: 'kubernetes',
        templateUrl: require('./patchManifestConfig.html'),
        controller: 'KubernetesV2PatchManifestConfigCtrl',
        controllerAs: 'ctrl',
        executionDetailsSections: [PatchStatus, ExecutionDetailsTasks, ExecutionArtifactTab],
        producesArtifacts: true,
        defaultTimeoutMs: 30 * 60 * 1000, // 30 minutes
        validators: [
          { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
          { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
          { type: 'manifestSelector' },
        ],
        artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['manifestArtifactId', 'requiredArtifactIds']),
        artifactRemover: ArtifactReferenceService.removeArtifactFromFields([
          'manifestArtifactId',
          'requiredArtifactIds',
        ]),
      });
    }
  })
  .controller('KubernetesV2PatchManifestConfigCtrl', KubernetesV2PatchManifestConfigCtrl);
