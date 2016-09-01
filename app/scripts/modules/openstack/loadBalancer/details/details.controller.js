'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.openstack.details.controller', [
    require('angular-ui-router'),
    require('../../../core/account/account.service.js'),
    require('../../../core/confirmationModal/confirmationModal.service.js'),
    require('../../../core/loadBalancer/loadBalancer.write.service.js'),
    require('../../../core/loadBalancer/loadBalancer.read.service.js'),
    require('../../../core/utils/lodash.js'),
    require('../../../core/confirmationModal/confirmationModal.service.js'),
    require('../../../core/insight/insightFilterState.model.js'),
    require('../../../core/utils/selectOnDblClick.directive.js'),
  ])
  .controller('openstackLoadBalancerDetailsController', function ($scope, $state, $uibModal, loadBalancer, app, InsightFilterStateModel,
                                                                  _, confirmationModalService, accountService, loadBalancerReader, loadBalancerWriter, subnetReader, $q) {

      let application = app;

      $scope.state = {
        loading: true
      };

      $scope.InsightFilterStateModel = InsightFilterStateModel;

      function extractLoadBalancer() {
        let [appLoadBalancer] = app.loadBalancers.data.filter(function (test) {
          return test.name === loadBalancer.name &&
            test.region === loadBalancer.region &&
            test.account === loadBalancer.accountId;
        });

        if (appLoadBalancer) {
          $scope.loadBalancer = appLoadBalancer;
          $scope.state.loading = false;

          $scope.lbLinks = [];

          angular.forEach($scope.loadBalancer.listeners, (value) => {
            if (value.externalProtocol.substring(0, 4).toLowerCase() === 'http') {
              $scope.lbLinks.push(value.externalProtocol.toLowerCase() + '://' + $scope.loadBalancer.ip + ':' + value.externalPort + '/');
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
          controller: 'openstackUpsertLoadBalancerController as ctrl',
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
          return loadBalancerWriter.deleteLoadBalancer(_.omit(loadBalancer, 'accountId'), application, {
            loadBalancerName: loadBalancer.name,
            id: $scope.loadBalancer.id,
            account: loadBalancer.accountId,
            region: loadBalancer.region
          });
        };

        confirmationModalService.confirm({
          header: 'Really delete ' + loadBalancer.name + '?',
          buttonText: 'Delete ' + loadBalancer.name,
          provider: 'openstack',
          account: loadBalancer.accountId,
          applicationName: application.name,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod
        });
      };
    }
  );
