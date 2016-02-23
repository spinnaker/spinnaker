'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.loadBalancer.controller', [
  require('../../../../../core/modal/wizard/v2modalWizard.service.js'),
])
  .controller('azureServerGroupLoadBalancersCtrl', function(v2modalWizardService) {
    v2modalWizardService.markClean('load-balancers');

    this.loadBalancerChanged = () => {
      v2modalWizardService.markComplete('load-balancers');
    };
  });
