'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.wizard.verticalScaling.component', [])
  .component('ecsServerGroupVerticalScaling', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./verticalScaling.component.html'),
  });
