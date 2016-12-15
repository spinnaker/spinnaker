'use strict';

import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';

let angular = require('angular');

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
