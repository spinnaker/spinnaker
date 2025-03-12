import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { ManifestImageDetails } from './ManifestImageDetails';

export const KUBERNETES_MANIFEST_IMAGE_DETAILS = 'spinnaker.kubernetes.v2.manifestImageDetails.component';
module(KUBERNETES_MANIFEST_IMAGE_DETAILS, []).component(
  'kubernetesManifestImageDetails',
  react2angular(withErrorBoundary(ManifestImageDetails, 'kubernetesManifestImageDetails'), ['manifest']),
);
