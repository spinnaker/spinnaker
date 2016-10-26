'use strict';

let angular = require('angular');
import _ from 'lodash';

module.exports = angular.module('spinnaker.deck.gce.loadBalancerChoice.modal.controller', [
    require('./loadBalancerTypeToWizardMap.constant.js')
  ])
  .controller('gceLoadBalancerChoiceCtrl', function ($uibModal, $uibModalInstance,
                                                     application, loadBalancerTypeToWizardMap) {
    this.app = application;
    this.choices = _.map(loadBalancerTypeToWizardMap, (wizardConfig) => wizardConfig.label);
    this.choice = 'Network';

    this.choose = (choice) => {
      let wizard = _.find(loadBalancerTypeToWizardMap, (wizardConfig) => wizardConfig.label === choice);
      $uibModalInstance.dismiss();
      $uibModal.open({
        templateUrl: wizard.createTemplateUrl,
        controller: `${wizard.controller} as ctrl`,
        size: 'lg',
        resolve: {
          application: () => this.app,
          loadBalancer: () => null,
          isNew: () => true,
          forPipelineConfig: () => false,
        }
      });
    };
  });
