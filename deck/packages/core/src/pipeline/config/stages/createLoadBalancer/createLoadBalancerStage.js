'use strict';

import * as angular from 'angular';
import { CloudProviderRegistry, ProviderSelectionService } from '../../../../cloudProvider';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_CREATELOADBALANCER_CREATELOADBALANCERSTAGE =
  'spinnaker.core.pipeline.stage.createLoadBalancerStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_CREATELOADBALANCER_CREATELOADBALANCERSTAGE; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_STAGES_CREATELOADBALANCER_CREATELOADBALANCERSTAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      key: 'upsertLoadBalancers',
      label: 'Create Load Balancers',
      description:
        'Creates one or more load balancers.' +
        ' If a load balancer exists with the same name, then that will be updated.',
      templateUrl: require('./createLoadBalancerStage.html'),
      executionDetailsUrl: require('./createLoadBalancerExecutionDetails.html'),
      supportsCustomTimeout: true,
      validators: [],
    });
  })
  .controller('createLoadBalancerStageCtrl', [
    '$scope',
    '$uibModal',
    function ($scope, $uibModal) {
      function initializeCommand() {
        $scope.stage.loadBalancers = $scope.stage.loadBalancers || [];
      }

      function appendLoadBalancers(loadBalancers) {
        if (Array.isArray(loadBalancers)) {
          $scope.stage.loadBalancers.push(...loadBalancers);
        } else if (loadBalancers) {
          $scope.stage.loadBalancers.push(loadBalancers);
        }
      }

      function updateLoadBalancerAtIndex(loadBalancer, index) {
        if (Array.isArray(loadBalancer)) {
          $scope.stage.loadBalancers.splice(index, 1, ...loadBalancer);
        } else if (loadBalancer) {
          $scope.stage.loadBalancers[index] = loadBalancer;
        }
      }

      this.addLoadBalancer = function () {
        ProviderSelectionService.selectProvider($scope.application, 'loadBalancer').then(function (selectedProvider) {
          const config = CloudProviderRegistry.getValue(selectedProvider, 'loadBalancer');
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
            .result.then(function (newLoadBalancer) {
              appendLoadBalancers(newLoadBalancer);
            })
            .catch(() => {});
        });
      };

      this.editLoadBalancer = function (loadBalancer, index) {
        ProviderSelectionService.selectProvider($scope.application, 'loadBalancer').then(function (selectedProvider) {
          const config = CloudProviderRegistry.getValue(selectedProvider, 'loadBalancer');
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
            .result.then(function (updatedLoadBalancer) {
              updateLoadBalancerAtIndex(updatedLoadBalancer, index);
            })
            .catch(() => {});
        });
      };

      this.copyLoadBalancer = function (index) {
        $scope.stage.loadBalancers.push(angular.copy($scope.stage.loadBalancers[index]));
      };

      this.removeLoadBalancer = function (index) {
        $scope.stage.loadBalancers.splice(index, 1);
      };

      initializeCommand();
    },
  ]);
