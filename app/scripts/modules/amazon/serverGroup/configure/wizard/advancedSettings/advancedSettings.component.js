'use strict';

const angular = require('angular');

import { V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.advancedSettings.component', [
    V2_MODAL_WIZARD_SERVICE,
  ])
  .component('awsServerGroupAdvancedSettings', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./advancedSettings.component.html'),
    controller: function(v2modalWizardService) {
      this.fieldChanged = () => {
        if (this.command.keyPair) {
          v2modalWizardService.markComplete('advanced');
        }
      };
    }
  });
