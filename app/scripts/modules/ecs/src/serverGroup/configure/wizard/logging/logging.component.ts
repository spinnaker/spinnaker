'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.wizard.logging.component', [])
  .component('ecsServerGroupLogging', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./logging.component.html'),
  });
