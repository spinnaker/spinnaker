import { module } from 'angular';
import { react2angular } from 'react2angular';
import { ManifestResources } from './ManifestResources';

export const KUBERNETES_MANIFEST_RESOURCES = 'spinnaker.kubernetes.v2.manifest.resources';
module(KUBERNETES_MANIFEST_RESOURCES, []).component(
  'kubernetesManifestResources',
  react2angular(ManifestResources, ['manifest', 'metrics']),
);
