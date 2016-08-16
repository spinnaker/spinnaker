'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.gce.details.controller', [
  require('angular-ui-router'),
  require('../../../core/account/account.service.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/loadBalancer/loadBalancer.write.service.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
  require('./hostAndPathRules/hostAndPathRulesButton.component.js'),
  require('./loadBalancerType/loadBalancerType.component.js'),
  require('../elSevenUtils.service.js'),
  require('./healthCheck/healthCheck.component.js'),
])
  .controller('gceLoadBalancerDetailsCtrl', function ($scope, $state, $uibModal, loadBalancer, app, InsightFilterStateModel,
                                                      _, confirmationModalService, accountService, elSevenUtils,
                                                      loadBalancerWriter, loadBalancerReader, $q) {

    let application = app;

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractLoadBalancer() {
      $scope.loadBalancer = application.loadBalancers.data.filter(function (test) {
        var testVpc = test.vpcId || null;
        return test.name === loadBalancer.name && (test.region === loadBalancer.region || test.region === 'global') && test.account === loadBalancer.accountId && testVpc === loadBalancer.vpcId;
      })[0];

      if ($scope.loadBalancer) {
        var detailsLoader = loadBalancerReader.getLoadBalancerDetails($scope.loadBalancer.provider, loadBalancer.accountId, $scope.loadBalancer.region, $scope.loadBalancer.name);
        return detailsLoader.then(function(details) {
          $scope.state.loading = false;
          var filtered = details.filter(function(test) {
            return test.vpcid === loadBalancer.vpcId || (!test.vpcid && !loadBalancer.vpcId);
          });
          if (filtered.length) {
            $scope.loadBalancer.elb = filtered[0];
            $scope.loadBalancer.account = loadBalancer.accountId;

            accountService.getCredentialsKeyedByAccount('gce').then(function(credentialsKeyedByAccount) {
              if (elSevenUtils.isElSeven($scope.loadBalancer)) {
                $scope.loadBalancer.elb.availabilityZones = [ 'All zones' ];
              } else {
                $scope.loadBalancer.elb.availabilityZones = _.find(credentialsKeyedByAccount[loadBalancer.accountId].regions, { name: loadBalancer.region }).zones.sort();
              }
            });
          }
          accountService.getAccountDetails(loadBalancer.accountId).then(function(accountDetails) {
            $scope.loadBalancer.logsLink =
              'https://console.developers.google.com/project/' + accountDetails.projectName + '/logs?service=compute.googleapis.com&minLogLevel=0&filters=text:' + $scope.loadBalancer.name;
          });
        },
          autoClose
        );
      }
      if (!$scope.loadBalancer) {
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

    app.loadBalancers.ready().then(extractLoadBalancer).then(() => {
      // If the user navigates away from the view before the initial extractLoadBalancer call completes,
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed) {
        app.loadBalancers.onRefresh($scope, extractLoadBalancer);
      }
    });

    this.editLoadBalancer = function editLoadBalancer() {
      $uibModal.open({
        templateUrl: require('../configure/editLoadBalancer.html'),
        controller: 'gceCreateLoadBalancerCtrl as ctrl',
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
          region: $scope.loadBalancer.region,
        });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        provider: 'gce',
        account: loadBalancer.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.isElSeven = elSevenUtils.isElSeven;
  }
);
