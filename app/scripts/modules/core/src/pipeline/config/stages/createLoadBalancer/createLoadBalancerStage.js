'use strict';

import { CloudProviderRegistry } from 'core/cloudProvider';
import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.createLoadBalancerStage', [])
  .config(function() {
    // Register this stage only if infrastructure stages are enabled in settings.js
    if (SETTINGS.feature.infrastructureStages) {
      Registry.pipeline.registerStage({
        key: 'upsertLoadBalancers',
        label: 'Create Load Balancers',
        description:
          'Creates one or more load balancers.' +
          ' If a load balancer exists with the same name, then that will be updated.',
        templateUrl: require('./createLoadBalancerStage.html'),
        executionDetailsUrl: require('./createLoadBalancerExecutionDetails.html'),
        defaultTimeoutMs: 5 * 60 * 1000, // 5 minutes
        validators: [],
      });
    }
  })
  .controller('createLoadBalancerStageCtrl', ['$scope', '$uibModal', 'providerSelectionService', function($scope, $uibModal, providerSelectionService) {
    function initializeCommand() {
      $scope.stage.loadBalancers = $scope.stage.loadBalancers || [];
    }

    this.addLoadBalancer = function() {
      providerSelectionService.selectProvider($scope.application, 'loadBalancer').then(function(selectedProvider) {
        let config = CloudProviderRegistry.getValue(selectedProvider, 'loadBalancer');
        $uibModal
          .open({
            templateUrl: config.createLoadBalancerTemplateUrl,
            controller: `${config.createLoadBalancerController} as ctrl`,
            size: 'lg',
            resolve: {
              application: () => $scope.application,
              loadBalancer: () => null,
              isNew: () => true,
              forPipelineConfig: () => true,
            },
          })
          .result.then(function(newLoadBalancer) {
            $scope.stage.loadBalancers.push(newLoadBalancer);
          })
          .catch(() => {});
      });
    };

    this.editLoadBalancer = function(loadBalancer, index) {
      providerSelectionService.selectProvider($scope.application, 'loadBalancer').then(function(selectedProvider) {
        let config = CloudProviderRegistry.getValue(selectedProvider, 'loadBalancer');
        $uibModal
          .open({
            templateUrl: config.createLoadBalancerTemplateUrl,
            controller: `${config.createLoadBalancerController} as ctrl`,
            size: 'lg',
            resolve: {
              application: () => $scope.application,
              loadBalancer: () => angular.copy(loadBalancer),
              isNew: () => false,
              forPipelineConfig: () => true,
            },
          })
          .result.then(function(updatedLoadBalancer) {
            $scope.stage.loadBalancers[index] = updatedLoadBalancer;
          })
          .catch(() => {});
      });
    };

    this.copyLoadBalancer = function(index) {
      $scope.stage.loadBalancers.push(angular.copy($scope.stage.loadBalancers[index]));
    };

    this.removeLoadBalancer = function(index) {
      $scope.stage.loadBalancers.splice(index, 1);
    };

    initializeCommand();
  }]);
