'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.kubernetes.container.commands.component', [])
  .component('kubernetesContainerCommands', {
    bindings: {
      commands: '=',
    },
    templateUrl: require('./commands.component.html'),
    controller: function () {
      if (!this.commands) {
        this.commands = [];
      }

      this.removeCommand = (index) => {
        this.commands.splice(index, 1);
      };

      this.addCommand = () => {
        this.commands.push('');
      };
    }
  });
