import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { ManifestSelector } from './ManifestSelector';

export const KUBERNETES_MANIFEST_SELECTOR = 'spinnaker.kubernetes.v2.manifest.selector.component';
module(KUBERNETES_MANIFEST_SELECTOR, []).component(
  'kubernetesManifestSelector',
  react2angular(withErrorBoundary(ManifestSelector, 'kubernetesManifestSelector'), [
    'selector',
    'modes',
    'application',
    'onChange',
  ]),
);
