'use strict';

import _ from 'lodash';

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

      this.envVarsSourceTypes = this.envVars
        .map((envVar) => {
          if (_.get(envVar, 'envSource.configMapSource')) {
            return 'Config Map';
          } else if (_.get(envVar, 'envSource.secretSource')) {
            return 'Secret';
          } else {
            return 'Explicit';
          }
        });

      this.removeEnvVar = function(index) {
        this.envVars.splice(index, 1);
        this.envVarsSourceTypes(index, 1);
      };

      this.addEnvVar = function() {
        this.envVars.push({});
        this.envVarsSourceTypes.push('Explicit');
      };

      this.sourceTypes = ['Explicit', 'Config Map', 'Secret'];

      this.updateSourceTypeMap = {
        'Explicit': (envVar) => {
          delete envVar.envSource;
        },
        'Config Map': (envVar) => {
          delete envVar.value;
          if (_.has(envVar, 'envSource.secretSource')) {
            delete envVar.envSource.secretSource;
          }
        },
        'Secret': (envVar) => {
          delete envVar.value;
          if (_.has(envVar, 'envSource.configMapSource')) {
            delete envVar.envSource.configMapSource;
          }
        }
      };

      this.updateEnvVar = (index) => {
        let envVar = this.envVars[index];
        let sourceType = this.envVarsSourceTypes[index];
        this.updateSourceTypeMap[sourceType](envVar);
      };
    }
  });
