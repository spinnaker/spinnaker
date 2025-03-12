import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';
import { ScaleSettingsForm } from './ScaleSettingsForm';

export const KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM =
  'spinnaker.kubernetes.v2.kubernetes.manifest.scale.settingsForm.component';
module(KUBERNETES_SCALE_MANIFEST_SETTINGS_FORM, []).component(
  'kubernetesScaleManifestSettingsForm',
  react2angular(withErrorBoundary(ScaleSettingsForm, 'kubernetesScaleManifestSettingsForm'), ['options', 'onChange']),
);
