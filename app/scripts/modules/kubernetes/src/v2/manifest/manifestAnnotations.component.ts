import { module } from 'angular';
import { react2angular } from 'react2angular';
import { ManifestAnnotations } from './ManifestAnnotations';

export const KUBERNETES_MANIFEST_ANNOTATIONS = 'spinnaker.kubernetes.v2.manifest.annotations';
module(KUBERNETES_MANIFEST_ANNOTATIONS, []).component(
  'kubernetesManifestAnnotations',
  react2angular(ManifestAnnotations, ['manifest']),
);
