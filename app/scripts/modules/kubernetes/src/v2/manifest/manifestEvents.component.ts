import { module } from 'angular';
import { react2angular } from 'react2angular';
import { ManifestEvents } from 'kubernetes/v2/pipelines/stages/deployManifest/manifestStatus/ManifestEvents';

export const KUBERNETES_MANIFEST_EVENTS = 'spinnaker.kubernetes.v2.manifest.events';
module(KUBERNETES_MANIFEST_EVENTS, []).component(
  'kubernetesManifestEvents',
  react2angular(ManifestEvents, ['manifest']),
);
