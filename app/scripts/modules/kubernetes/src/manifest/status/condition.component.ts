import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { ManifestCondition } from './ManifestCondition';

export const KUBERNETES_MANIFEST_CONDITION = 'spinnaker.kubernetes.v2.manifest.condition.component';
module(KUBERNETES_MANIFEST_CONDITION, []).component(
  'kubernetesManifestCondition',
  react2angular(withErrorBoundary(ManifestCondition, 'kubernetesManifestCondition'), ['condition']),
);
