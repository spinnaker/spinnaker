'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.openstack.serverGroup.configure.wizard.advancedSettings.component', [
    require('../../../../../core/modal/wizard/v2modalWizard.service.js'),
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
