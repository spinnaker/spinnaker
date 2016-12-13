'use strict';

let angular = require('angular');
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';

module.exports = angular.module('spinnaker.serverGroup.configure.aws.loadBalancer.controller', [
  require('core/modal/wizard/modalWizard.service.js'),
  INFRASTRUCTURE_CACHE_SERVICE,
  require('../../serverGroupConfiguration.service.js'),
])
  .controller('awsServerGroupLoadBalancersCtrl', function($scope, modalWizardService, infrastructureCaches,
                                                          awsServerGroupConfigurationService) {

    $scope.getLoadBalancerRefreshTime = function() {
      return infrastructureCaches.get('loadBalancers').getStats().ageMax;
    };

    $scope.refreshLoadBalancers = function() {
      $scope.refreshing = true;
      awsServerGroupConfigurationService.refreshLoadBalancers($scope.command).then(function() {
        $scope.refreshing = false;
      });
    };


  });
