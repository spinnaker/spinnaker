'use strict';

const angular = require('angular');

import { V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.wizard.verticalScaling.component', [V2_MODAL_WIZARD_SERVICE])
  .component('ecsServerGroupVerticalScaling', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./verticalScaling.component.html'),
  });
