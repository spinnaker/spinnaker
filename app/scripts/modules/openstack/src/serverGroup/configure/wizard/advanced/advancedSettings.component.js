'use strict';

const angular = require('angular');

import { V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.serverGroup.configure.wizard.advancedSettings.component', [
    V2_MODAL_WIZARD_SERVICE,
  ])
  .component('openstackServerGroupAdvancedSettings', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./advancedSettings.component.html'),
    controller: function(v2modalWizardService) {
      this.fieldChanged = () => {
        if (this.command.userData) {
          v2modalWizardService.markComplete('advanced-settings');
        }
      };
    }
  });
