'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_CONTAINER_COMMANDS_COMPONENT = 'spinnaker.deck.kubernetes.container.commands.component';
export const name = KUBERNETES_V1_CONTAINER_COMMANDS_COMPONENT; // for backwards compatibility
module(KUBERNETES_V1_CONTAINER_COMMANDS_COMPONENT, []).component('kubernetesContainerCommands', {
  bindings: {
    commands: '=',
  },
  templateUrl: require('./commands.component.html'),
  controller: function() {
    if (!this.commands) {
      this.commands = [];
    }

    this.removeCommand = index => {
      this.commands.splice(index, 1);
    };

    this.addCommand = () => {
      this.commands.push('');
    };
  },
});
