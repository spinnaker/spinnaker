'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.kubernetes.environmentVariables.component', [])
  .component('kubernetesContainerEnvironmentVariables', {
    bindings: {
      envVars: '='
    },
    templateUrl: require('./environmentVariables.component.html'),
    controller: function () {
      if (!this.envVars) {
        this.envVars = [];
      }

      this.removeEnvVar = function(index) {
        this.envVars.splice(index, 1);
      };

      this.addEnvVar = function() {
        this.envVars.push({ name: '', value: '', });
      };
    }
  });
