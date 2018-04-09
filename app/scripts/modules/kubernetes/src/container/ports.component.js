'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.container.ports.component', [])
  .component('kubernetesContainerPorts', {
    bindings: {
      ports: '=',
    },
    templateUrl: require('./ports.component.html'),
    controller: function() {
      this.protocols = ['TCP', 'UDP'];
      this.maxPort = 65535;

      if (!this.ports) {
        this.ports = [];
      }

      this.removePort = index => {
        this.ports.splice(index, 1);
      };

      this.addPort = () => {
        this.ports.push({
          name: 'http',
          containerPort: 80,
          protocol: 'TCP',
          hostPort: null,
          hostIp: null,
        });
      };
    },
  });
