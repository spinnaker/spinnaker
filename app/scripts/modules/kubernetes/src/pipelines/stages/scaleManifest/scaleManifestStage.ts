import { module } from 'angular';

import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';

import { manifestExecutionDetails } from '../ManifestExecutionDetails';
import { KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM } from '../../../manifest/scale/scaleSettingsForm.component';
import { KubernetesV2ScaleManifestConfigCtrl } from './scaleManifestConfig.controller';

export const KUBERNETES_SCALE_MANIFEST_STAGE = 'spinnaker.kubernetes.v2.pipeline.stage.scaleManifestStage';
const STAGE_KEY = 'scaleManifest';

module(KUBERNETES_SCALE_MANIFEST_STAGE, [KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Scale (Manifest)',
      description: 'Scale a Kubernetes object created from a manifest.',
      key: STAGE_KEY,
      cloudProvider: 'kubernetes',
      templateUrl: require('./scaleManifestConfig.html'),
      controller: 'KubernetesV2ScaleManifestConfigCtrl',
      controllerAs: 'ctrl',
      executionDetailsSections: [manifestExecutionDetails(STAGE_KEY), ExecutionDetailsTasks],
      accountExtractor: (stage: IStage): string[] => (stage.account ? [stage.account] : []),
      configAccountExtractor: (stage: any): string[] => (stage.account ? [stage.account] : []),
      validators: [
        { type: 'requiredField', fieldName: 'location', fieldLabel: 'Namespace' },
        { type: 'requiredField', fieldName: 'account', fieldLabel: 'Account' },
        { type: 'requiredField', fieldName: 'replicas', fieldLabel: 'Replicas' },
      ],
    });
  })
  .controller('KubernetesV2ScaleManifestConfigCtrl', KubernetesV2ScaleManifestConfigCtrl);
