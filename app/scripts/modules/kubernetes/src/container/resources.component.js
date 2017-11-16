'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.kubernetes.container.resources.component', [])
  .component('kubernetesContainerResources', {
    bindings: {
      container: '='
    },
    templateUrl: require('./resources.component.html'),
    controller: function() {
      this.cpuPattern = /^\d+(m)?$/;
      this.memoryPattern = /^\d+(Mi|Gi)?$/;
    }
  });
