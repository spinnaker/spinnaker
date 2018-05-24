'use strict';

const angular = require('angular');

import { ModalWizard } from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.advancedSettings.component', [])
  .component('awsServerGroupAdvancedSettings', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./advancedSettings.component.html'),
    controller: function() {
      this.fieldChanged = () => {
        if (this.command.keyPair) {
          ModalWizard.markComplete('advanced');
        }
      };

      this.disableSpotPricing = AWSProviderSettings.disableSpotPricing;
    },
  });
