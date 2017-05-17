'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.kubernetes.arguments.component', [])
  .component('kubernetesContainerArguments', {
    bindings: {
      args: '=',
    },
    templateUrl: require('./arguments.component.html'),
    controller: function () {
      if (!this.args) {
        this.args = [];
      }

      this.removeArg = (index) => {
        this.args.splice(index, 1);
      };

      this.addArg = () => {
        this.args.push('');
      };
    }
  });
