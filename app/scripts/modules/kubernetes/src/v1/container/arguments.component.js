'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_CONTAINER_ARGUMENTS_COMPONENT = 'spinnaker.deck.kubernetes.arguments.component';
export const name = KUBERNETES_V1_CONTAINER_ARGUMENTS_COMPONENT; // for backwards compatibility
module(KUBERNETES_V1_CONTAINER_ARGUMENTS_COMPONENT, []).component('kubernetesContainerArguments', {
  bindings: {
    args: '=',
  },
  templateUrl: require('./arguments.component.html'),
  controller: function() {
    if (!this.args) {
      this.args = [];
    }

    this.removeArg = index => {
      this.args.splice(index, 1);
    };

    this.addArg = () => {
      this.args.push('');
    };
  },
});
