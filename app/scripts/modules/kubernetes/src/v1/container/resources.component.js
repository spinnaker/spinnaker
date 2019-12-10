'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_CONTAINER_RESOURCES_COMPONENT = 'spinnaker.deck.kubernetes.container.resources.component';
export const name = KUBERNETES_V1_CONTAINER_RESOURCES_COMPONENT; // for backwards compatibility
module(KUBERNETES_V1_CONTAINER_RESOURCES_COMPONENT, []).component('kubernetesContainerResources', {
  bindings: {
    container: '=',
  },
  templateUrl: require('./resources.component.html'),
  controller: function() {
    this.cpuPattern = /^\d+(m)?$/;
    this.memoryPattern = /^\d+(Mi|Gi)?$/;
  },
});
