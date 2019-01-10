'use strict';

const angular = require('angular');

import { CONFIRMATION_MODAL_SERVICE, LoadBalancerWriter } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.loadBalancer.openstack.details.controller', [
    require('@uirouter/angularjs').default,
    CONFIRMATION_MODAL_SERVICE,
  ])
  .controller('openstackLoadBalancerDetailsController', function(
    $scope,
    $state,
    $uibModal,
    loadBalancer,
    app,
    confirmationModalService,
    $q,
  ) {
    let application = app;

    $scope.state = {
      loading: true,
    };

    function extractLoadBalancer() {
      let appLoadBalancer = app.loadBalancers.data.find(function(test) {
        return (
          test.name === loadBalancer.name &&
          test.region === loadBalancer.region &&
          test.account === loadBalancer.accountId
        );
      });

      if (appLoadBalancer) {
        $scope.loadBalancer = appLoadBalancer;
        $scope.state.loading = false;

        $scope.lbLinks = [];

        angular.forEach($scope.loadBalancer.listeners, value => {
          if (value.externalProtocol.substring(0, 4).toLowerCase() === 'http') {
            $scope.lbLinks.push(
              value.externalProtocol.toLowerCase() + '://' + $scope.loadBalancer.ip + ':' + value.externalPort + '/',
            );
          }
        });
      } else {
        autoClose();
      }

      return $q.when(null);
    }

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
        controller: 'openstackUpsertLoadBalancerController as ctrl',
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
        cloudProvider: 'openstack',
        loadBalancerName: $scope.loadBalancer.name,
        id: $scope.loadBalancer.id,
        region: $scope.loadBalancer.region,
        account: $scope.loadBalancer.account,
        credentials: $scope.loadBalancer.account,
      };

      const submitMethod = () => LoadBalancerWriter.deleteLoadBalancer(command, application);

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        provider: 'openstack',
        account: loadBalancer.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
      });
    };
  });
