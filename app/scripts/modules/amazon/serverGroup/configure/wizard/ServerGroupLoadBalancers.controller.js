'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws.loadBalancer.controller', [
  require('../../../../core/modal/wizard/modalWizard.service.js'),
  require('../../../../core/cache/infrastructureCaches.js'),
  require('../serverGroupConfiguration.service.js'),
])
  .controller('awsServerGroupLoadBalancersCtrl', function($scope, modalWizardService, infrastructureCaches,
                                                          awsServerGroupConfigurationService) {

    modalWizardService.getWizard().markClean('load-balancers');
    modalWizardService.getWizard().markComplete('load-balancers');

    $scope.getLoadBalancerRefreshTime = function() {
      return infrastructureCaches.loadBalancers.getStats().ageMax;
    };

    $scope.refreshLoadBalancers = function() {
      $scope.refreshing = true;
      awsServerGroupConfigurationService.refreshLoadBalancers($scope.command).then(function() {
        $scope.refreshing = false;
      });
    };


  });
