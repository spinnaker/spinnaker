'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws.loadBalancer.controller', [
  require('../../../../core/modal/wizard/modalWizard.service.js'),
])
  .controller('awsServerGroupLoadBalancersCtrl', function(modalWizardService) {
    modalWizardService.getWizard().markClean('load-balancers');
    modalWizardService.getWizard().markComplete('load-balancers');

  }).name;
