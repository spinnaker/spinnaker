'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { CONFIRMATION_MODAL_SERVICE, LoadBalancerWriter, ServerGroupTemplates } from '@spinnaker/core';

import { KubernetesProviderSettings } from 'kubernetes/kubernetes.settings';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';

export const KUBERNETES_V1_LOADBALANCER_DETAILS_DETAILS_CONTROLLER =
  'spinnaker.loadBalancer.kubernetes.details.controller';
export const name = KUBERNETES_V1_LOADBALANCER_DETAILS_DETAILS_CONTROLLER; // for backwards compatibility
angular
  .module(KUBERNETES_V1_LOADBALANCER_DETAILS_DETAILS_CONTROLLER, [UIROUTER_ANGULARJS, CONFIRMATION_MODAL_SERVICE])
  .controller('kubernetesLoadBalancerDetailsController', [
    '$interpolate',
    '$scope',
    '$state',
    '$uibModal',
    'loadBalancer',
    'app',
    'confirmationModalService',
    'kubernetesProxyUiService',
    function(
      $interpolate,
      $scope,
      $state,
      $uibModal,
      loadBalancer,
      app,
      confirmationModalService,
      kubernetesProxyUiService,
    ) {
      const application = app;

      $scope.state = {
        loading: true,
      };

      const extractLoadBalancer = () => {
        return application.loadBalancers.ready().then(() => {
          $scope.loadBalancer = application.loadBalancers.data.find(test => {
            return (
              test.name === loadBalancer.name &&
              (test.namespace === loadBalancer.region || test.namespace === loadBalancer.namespace) &&
              test.account === loadBalancer.accountId
            );
          });

          if ($scope.loadBalancer) {
            this.ingressProtocol = 'http:';
            if (_.get($scope.loadBalancer, 'service.spec.ports', []).some(p => p.port === 443)) {
              this.ingressProtocol = 'https:';
            }
            this.internalDNSName = $interpolate(
              KubernetesProviderSettings.defaults.internalDNSNameTemplate || '{{name}}.{{namespace}}.svc.cluster.local',
            )($scope.loadBalancer);
            $scope.state.loading = false;
          } else {
            autoClose();
          }
        });
      };

      this.uiLink = function uiLink() {
        return kubernetesProxyUiService.buildLink(
          $scope.loadBalancer.account,
          'service',
          $scope.loadBalancer.region,
          $scope.loadBalancer.name,
        );
      };

      this.showYaml = function showYaml() {
        $scope.userDataModalTitle = 'Service YAML';
        $scope.userData = $scope.loadBalancer.yaml;
        $uibModal.open({
          templateUrl: ServerGroupTemplates.userData,
          scope: $scope,
        });
      };

      function autoClose() {
        if ($scope.$$destroyed) {
          return;
        }
        $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
      }

      extractLoadBalancer().then(() => {
        // If the user navigates away from the view before the initial extractLoadBalancer call completes,
        // do not bother subscribing to the refresh
        if (!$scope.$$destroyed) {
          app.loadBalancers.onRefresh($scope, extractLoadBalancer);
        }
      });

      this.editLoadBalancer = function editLoadBalancer() {
        $uibModal.open({
          templateUrl: require('../configure/wizard/editWizard.html'),
          controller: 'kubernetesUpsertLoadBalancerController as ctrl',
          size: 'lg',
          resolve: {
            application: function() {
              return application;
            },
            loadBalancer: function() {
              return angular.copy($scope.loadBalancer);
            },
            isNew: function() {
              return false;
            },
          },
        });
      };

      this.deleteLoadBalancer = function deleteLoadBalancer() {
        if ($scope.loadBalancer.instances && $scope.loadBalancer.instances.length) {
          return;
        }

        const taskMonitor = {
          application: application,
          title: 'Deleting ' + loadBalancer.name,
        };

        const command = {
          cloudProvider: 'kubernetes',
          loadBalancerName: $scope.loadBalancer.name,
          credentials: $scope.loadBalancer.account,
          namespace: loadBalancer.region,
        };

        const submitMethod = () => LoadBalancerWriter.deleteLoadBalancer(command, application);

        confirmationModalService.confirm({
          header: 'Really delete ' + loadBalancer.name + '?',
          buttonText: 'Delete ' + loadBalancer.name,
          provider: 'kubernetes',
          account: loadBalancer.account,
          applicationName: application.name,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };
    },
  ]);
