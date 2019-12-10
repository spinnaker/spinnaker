'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_CONTAINER_PORTS_COMPONENT = 'spinnaker.kubernetes.container.ports.component';
export const name = KUBERNETES_V1_CONTAINER_PORTS_COMPONENT; // for backwards compatibility
module(KUBERNETES_V1_CONTAINER_PORTS_COMPONENT, []).component('kubernetesContainerPorts', {
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
