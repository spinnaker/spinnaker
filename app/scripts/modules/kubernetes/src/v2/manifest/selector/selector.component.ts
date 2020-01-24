import { module } from 'angular';

import { react2angular } from 'react2angular';
import { ManifestSelector } from './ManifestSelector';

export const KUBERNETES_MANIFEST_SELECTOR = 'spinnaker.kubernetes.v2.manifest.selector.component';
module(KUBERNETES_MANIFEST_SELECTOR, []).component(
  'kubernetesManifestSelector',
  react2angular(ManifestSelector, ['selector', 'modes', 'application', 'onChange']),
);
