'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancerChoice.modal.controller', [
    require('./loadBalancerTypeToWizardMap.constant.js')
  ])
  .controller('gceLoadBalancerChoiceCtrl', function ($uibModal, $uibModalInstance,
                                                     application, loadBalancerTypeToWizardMap) {
    this.app = application;
    this.choices = Object.keys(loadBalancerTypeToWizardMap);
    this.choice = 'Network';

    this.choose = (choice) => {
      let wizard = loadBalancerTypeToWizardMap[choice];
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
