import { module } from 'angular';
import { react2angular } from 'react2angular';

import { KubernetesTolerations } from './TolerationsComponent';

export const KUBERNETES_TOLERATIONS = 'spinnaker.kubernetes.tolerations.component';
module(KUBERNETES_TOLERATIONS, []).component(
  'kubernetesTolerations',
  react2angular(KubernetesTolerations, ['tolerations', 'onTolerationChange']),
);
