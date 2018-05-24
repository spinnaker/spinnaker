'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.wizard.horizontalScaling.component', [])
  .component('ecsServerGroupHorizontalScaling', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./horizontalScaling.component.html'),
  });
