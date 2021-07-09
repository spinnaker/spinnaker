'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';
import _ from 'lodash';

import {
  ConfirmationModalService,
  FirewallLabels,
  LOAD_BALANCER_READ_SERVICE,
  LoadBalancerWriter,
  SECURITY_GROUP_READER,
} from '@spinnaker/core';

export const AZURE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER =
  'spinnaker.azure.loadBalancer.details.controller';
export const name = AZURE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER; // for backwards compatibility
angular
  .module(AZURE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER, [
    ANGULAR_UI_BOOTSTRAP,
    UIROUTER_ANGULARJS,
    SECURITY_GROUP_READER,
    LOAD_BALANCER_READ_SERVICE,
  ])
  .controller('azureLoadBalancerDetailsCtrl', [
    '$scope',
    '$state',
    '$exceptionHandler',
    '$uibModal',
    'loadBalancer',
    'app',
    'securityGroupReader',
    'loadBalancerReader',
    '$q',
    function (
      $scope,
      $state,
      $exceptionHandler,
      $uibModal,
      loadBalancer,
      app,
      securityGroupReader,
      loadBalancerReader,
      $q,
    ) {
      $scope.state = {
        loading: true,
      };

      $scope.firewallsLabel = FirewallLabels.get('Firewalls');

      function extractLoadBalancer() {
        $scope.loadBalancer = app.loadBalancers.data.filter(function (test) {
          return (
            test.name === loadBalancer.name &&
            test.region === loadBalancer.region &&
            test.account === loadBalancer.accountId
          );
        })[0];

        if ($scope.loadBalancer) {
          const detailsLoader = loadBalancerReader.getLoadBalancerDetails(
            $scope.loadBalancer.provider,
            loadBalancer.accountId,
            loadBalancer.region,
            loadBalancer.name,
          );

          return detailsLoader.then(function (details) {
            $scope.state.loading = false;
            const securityGroups = [];

            const filtered = details.filter(function (test) {
              return test.name === loadBalancer.name;
            });

            if (filtered.length) {
              $scope.loadBalancer.elb = filtered[0];

              $scope.loadBalancer.account = loadBalancer.accountId;

              if ($scope.loadBalancer.elb.securityGroups) {
                $scope.loadBalancer.elb.securityGroups.forEach(function (securityGroupId) {
                  const match = securityGroupReader.getApplicationSecurityGroup(
                    app,
                    loadBalancer.accountId,
                    loadBalancer.region,
                    securityGroupId,
                  );
                  if (match) {
                    securityGroups.push(match);
                  }
                });
                $scope.securityGroups = _.sortBy(securityGroups, 'name');
              }

              if ($scope.loadBalancer.loadBalancerType && $scope.loadBalancer.loadBalancerType.includes('_')) {
                const type = $scope.loadBalancer.loadBalancerType;
                $scope.loadBalancer.loadBalancerType = type
                  .split('_')
                  .map((s) => {
                    const ss = s.toLowerCase();
                    return ss.substring(0, 1).toUpperCase() + ss.substring(1);
                  })
                  .join(' ');
              }
            }
          });
        }
        if (!$scope.loadBalancer) {
          $state.go('^');
        }

        return $q.when(null);
      }

      app
        .ready()
        .then(extractLoadBalancer)
        .then(() => {
          // If the user navigates away from the view before the initial extractLoadBalancer call completes,
          // do not bother subscribing to the refresh
          if (!$scope.$$destroyed) {
            app.onRefresh($scope, extractLoadBalancer);
          }
        });

      this.editLoadBalancer = function editLoadBalancer() {
        $uibModal.open({
          templateUrl: require('../configure/editLoadBalancer.html'),
          controller: 'azureCreateLoadBalancerCtrl as ctrl',
          size: 'lg',
          resolve: {
            application: function () {
              return app;
            },
            loadBalancer: function () {
              return angular.copy($scope.loadBalancer);
            },
            isNew: function () {
              return false;
            },
            loadBalancerType: function () {
              return { type: $scope.loadBalancer.loadBalancerType };
            },
          },
        });
      };

      this.deleteLoadBalancer = function deleteLoadBalancer() {
        if ($scope.loadBalancer.instances && $scope.loadBalancer.instances.length) {
          return;
        }

        const taskMonitor = {
          application: app,
          title: 'Deleting ' + loadBalancer.name,
        };

        const command = {
          cloudProvider: 'azure',
          loadBalancerName: $scope.loadBalancer.name,
          loadBalancerType: $scope.loadBalancer.loadBalancerType,
          credentials: $scope.loadBalancer.account,
          region: loadBalancer.region,
          appName: app.name,
        };

        const submitMethod = () => LoadBalancerWriter.deleteLoadBalancer(command, app);

        ConfirmationModalService.confirm({
          header: 'Really delete ' + loadBalancer.name + '?',
          buttonText: 'Delete ' + loadBalancer.name,
          account: loadBalancer.accountId,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };
    },
  ]);
