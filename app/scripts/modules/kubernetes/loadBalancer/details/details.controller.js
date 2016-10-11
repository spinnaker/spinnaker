'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.kubernetes.details.controller', [
  require('angular-ui-router'),
  require('core/account/account.service.js'),
  require('core/confirmationModal/confirmationModal.service.js'),
  require('core/loadBalancer/loadBalancer.write.service.js'),
  require('core/loadBalancer/loadBalancer.read.service.js'),
  require('core/confirmationModal/confirmationModal.service.js'),
  require('core/insight/insightFilterState.model.js'),
  require('core/utils/selectOnDblClick.directive.js'),
])
  .controller('kubernetesLoadBalancerDetailsController', function ($scope, $state, $uibModal, loadBalancer, app, InsightFilterStateModel,
                                                                   confirmationModalService, accountService, loadBalancerWriter, loadBalancerReader, $q) {

    let application = app;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractLoadBalancer() {
      $scope.loadBalancer = application.loadBalancers.data.filter(function (test) {
        return test.name === loadBalancer.name &&
          (test.namespace === loadBalancer.region || test.namespace === loadBalancer.namespace) &&
          test.account === loadBalancer.accountId;
      })[0];

      if ($scope.loadBalancer) {
        $scope.state.loading = false;
      } else {
        autoClose();
      }

      return $q.when(null);
    }

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

      var taskMonitor = {
        application: application,
        title: 'Deleting ' + loadBalancer.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        loadBalancer.providerType = $scope.loadBalancer.provider;
        return loadBalancerWriter.deleteLoadBalancer(loadBalancer, application, {
          loadBalancerName: loadBalancer.name,
          namespace: loadBalancer.region,
        });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        provider: 'kubernetes',
        account: loadBalancer.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };
  }
);
