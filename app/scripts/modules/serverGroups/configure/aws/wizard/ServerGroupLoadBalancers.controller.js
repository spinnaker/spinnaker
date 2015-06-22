'use strict';

require('./loadBalancers.html');

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws.loadBalancer.controller', [
  require('../../../../../directives/modalWizard.js'),
])
  .controller('awsServerGroupLoadBalancersCtrl', function(modalWizardService) {
    modalWizardService.getWizard().markClean('load-balancers');
    modalWizardService.getWizard().markComplete('load-balancers');

  }).name;
