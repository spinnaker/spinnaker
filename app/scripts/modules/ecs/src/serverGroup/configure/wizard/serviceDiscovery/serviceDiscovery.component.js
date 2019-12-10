'use strict';

const angular = require('angular');

export const ECS_SERVERGROUP_CONFIGURE_WIZARD_SERVICEDISCOVERY_SERVICEDISCOVERY_COMPONENT =
  'spinnaker.ecs.serverGroup.configure.wizard.serviceDiscovery.component';
export const name = ECS_SERVERGROUP_CONFIGURE_WIZARD_SERVICEDISCOVERY_SERVICEDISCOVERY_COMPONENT; // for backwards compatibility
angular
  .module(ECS_SERVERGROUP_CONFIGURE_WIZARD_SERVICEDISCOVERY_SERVICEDISCOVERY_COMPONENT, [])
  .component('ecsServerGroupServiceDiscovery', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./serviceDiscovery.component.html'),
  });
