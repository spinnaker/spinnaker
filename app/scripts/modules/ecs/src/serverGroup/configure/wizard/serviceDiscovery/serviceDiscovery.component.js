'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.wizard.serviceDiscovery.component', [])
  .component('ecsServerGroupServiceDiscovery', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./serviceDiscovery.component.html'),
  });
