'use strict';

const angular = require('angular');

import { V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.wizard.horizontalScaling.component', [
    V2_MODAL_WIZARD_SERVICE,
  ])
  .component('ecsServerGroupHorizontalScaling', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./horizontalScaling.component.html'),
  });
