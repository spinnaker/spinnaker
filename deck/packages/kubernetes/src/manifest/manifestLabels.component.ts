import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { ManifestLabels } from './ManifestLabels';

export const KUBERNETES_MANIFEST_LABELS = 'spinnaker.kubernetes.v2.manifest.labels';
module(KUBERNETES_MANIFEST_LABELS, []).component(
  'kubernetesManifestLabels',
  react2angular(withErrorBoundary(ManifestLabels, 'kubernetesManifestLabels'), ['manifest']),
);
