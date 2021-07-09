import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { ManifestQos } from './ManifestQos';

export const KUBERNETES_MANIFEST_QOS = 'spinnaker.kubernetes.v2.manifest.qos';
module(KUBERNETES_MANIFEST_QOS, []).component(
  'kubernetesManifestQos',
  react2angular(withErrorBoundary(ManifestQos, 'kubernetesManifestQos'), ['manifest']),
);
