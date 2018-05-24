'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.wizard.advancedSettings.component', [])
  .component('ecsServerGroupAdvancedSettings', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./advancedSettings.component.html'),
  });
