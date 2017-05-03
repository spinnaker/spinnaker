'use strict';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {LOAD_BALANCER_WRITE_SERVICE} from 'core/loadBalancer/loadBalancer.write.service';
import {KubernetesProviderSettings} from 'kubernetes/kubernetes.settings';

let angular = require('angular');
import _ from 'lodash';

module.exports = angular.module('spinnaker.loadBalancer.kubernetes.details.controller', [
  require('angular-ui-router').default,
  ACCOUNT_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
  LOAD_BALANCER_WRITE_SERVICE,
  require('core/utils/selectOnDblClick.directive.js'),
])
  .controller('kubernetesLoadBalancerDetailsController', function ($interpolate, $scope, $state, $uibModal, loadBalancer, app,
                                                                   confirmationModalService, accountService, loadBalancerWriter,
                                                                   kubernetesProxyUiService) {

    let application = app;

    $scope.state = {
      loading: true
    };

    let extractLoadBalancer = () => {
      return application.loadBalancers.ready()
        .then(() => {
          $scope.loadBalancer = application.loadBalancers.data.find((test) => {
            return test.name === loadBalancer.name &&
              (test.namespace === loadBalancer.region || test.namespace === loadBalancer.namespace) &&
              test.account === loadBalancer.accountId;
          });

          if ($scope.loadBalancer) {
            this.ingressProtocol = 'http:';
            if (_.get($scope.loadBalancer, 'service.spec.ports', []).some(p => p.port === 443)) {
              this.ingressProtocol = 'https:';
            }
            this.internalDNSName = $interpolate(
              KubernetesProviderSettings.defaults.internalDNSNameTemplate || '{{name}}.{{namespace}}.svc.cluster.local'
            )($scope.loadBalancer);
            $scope.state.loading = false;
          } else {
            autoClose();
          }
        });
    };

    this.uiLink = function uiLink() {
      return kubernetesProxyUiService.buildLink($scope.loadBalancer.account, 'service', $scope.loadBalancer.region, $scope.loadBalancer.name);
    };

    this.showYaml = function showYaml() {
      $scope.userDataModalTitle = 'Service YAML';
      $scope.userData = $scope.loadBalancer.yaml;
      $uibModal.open({
        templateUrl: require('core/serverGroup/details/userData.html'),
        controller: 'CloseableModalCtrl',
        scope: $scope
      });
    };

    function autoClose() {
      if ($scope.$$destroyed) {
        return;
      }
      $state.params.allowModalToStayOpen = true;
      $state.go('^', null, {location: 'replace'});
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
          application: function() { return application; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); },
          isNew: function() { return false; }
        }
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

      const submitMethod = () => loadBalancerWriter.deleteLoadBalancer(command, application);

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        provider: 'kubernetes',
        account: loadBalancer.account,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };
  }
);
