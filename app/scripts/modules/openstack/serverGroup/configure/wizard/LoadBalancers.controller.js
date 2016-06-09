
'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.serverGroup.configure.loadBalancers', [])
  .controller('openstacksServerGroupLoadBalancersCtrl', function(modalWizardService) {
    modalWizardService.getWizard().markClean('load-balancers');
    modalWizardService.getWizard().markComplete('load-balancers');
  });
