'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws')
  .controller('gceServerGroupLoadBalancersCtrl', function(modalWizardService) {
    modalWizardService.getWizard().markClean('load-balancers');
    modalWizardService.getWizard().markComplete('load-balancers');

  }).name;
