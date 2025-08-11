import { module } from 'angular';

import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';
import { ManifestStatus } from './ManifestStatus';

export const KUBERNETES_MANIFEST_STATUS = 'spinnaker.kubernetes.v2.kubernetes.manifest.status.component';
module(KUBERNETES_MANIFEST_STATUS, []).component(
  'kubernetesManifestStatus',
  react2angular(withErrorBoundary(ManifestStatus, 'kubernetesManifestStatus'), ['status']),
);
