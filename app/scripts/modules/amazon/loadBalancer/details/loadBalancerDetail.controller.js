'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.aws.details.controller', [
  require('angular-ui-router'),
  require('../../../core/securityGroup/securityGroup.read.service.js'),
  require('../../../core/loadBalancer/loadBalancer.write.service.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('../../../core/presentation/collapsibleSection/collapsibleSection.directive.js'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
])
  .controller('awsLoadBalancerDetailsCtrl', function ($scope, $state, $uibModal, loadBalancer, app, InsightFilterStateModel,
                                                   securityGroupReader, _, confirmationModalService, loadBalancerWriter, loadBalancerReader, $q) {

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractLoadBalancer() {
      if (!loadBalancer.vpcId) {
        loadBalancer.vpcId = null;
      }
      $scope.loadBalancer = app.loadBalancers.filter(function (test) {
        var testVpc = test.vpcId || null;
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId && testVpc === loadBalancer.vpcId;
      })[0];

      if ($scope.loadBalancer) {
        var detailsLoader = loadBalancerReader.getLoadBalancerDetails($scope.loadBalancer.provider, loadBalancer.accountId, loadBalancer.region, loadBalancer.name);
        return detailsLoader.then(function(details) {
          $scope.state.loading = false;
          var securityGroups = [];
          var filtered = details.filter(function(test) {
            return test.vpcid === loadBalancer.vpcId || (!test.vpcid && !loadBalancer.vpcId);
          });
          if (filtered.length) {
            $scope.loadBalancer.elb = filtered[0];
            $scope.loadBalancer.account = loadBalancer.accountId;

            if ($scope.loadBalancer.elb.availabilityZones) {
              $scope.loadBalancer.elb.availabilityZones.sort();
            }

            $scope.loadBalancer.elb.securityGroups.forEach(function (securityGroupId) {
              var match = securityGroupReader.getApplicationSecurityGroup(app, loadBalancer.accountId, loadBalancer.region, securityGroupId);
              if (match) {
                securityGroups.push(match);
              }
            });
            $scope.securityGroups = _.sortBy(securityGroups, 'name');
          }
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

    extractLoadBalancer().then(() => {
      // If the user navigates away from the view before the initial extractLoadBalancer call completes,
      // do not bother subscribing to the autoRefreshStream
      if (!$scope.$$destroyed) {
        let refreshWatcher = app.autoRefreshStream.subscribe(extractLoadBalancer);
        $scope.$on('$destroy', () => refreshWatcher.dispose());
      }
    });

    this.editLoadBalancer = function editLoadBalancer() {
      $uibModal.open({
        templateUrl: require('../configure/editLoadBalancer.html'),
        controller: 'awsCreateLoadBalancerCtrl as ctrl',
        resolve: {
          application: function() { return app; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); },
          isNew: function() { return false; }
        }
      });
    };

    this.cloneLoadBalancer = function () {
      $uibModal.open({
        templateUrl: require('../configure/createLoadBalancer.html'),
        controller: 'awsCreateLoadBalancerCtrl as ctrl',
        resolve: {
          application: function() { return app; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); },
          isNew: function() { return true; }
        }
      });
    };

    this.deleteLoadBalancer = function deleteLoadBalancer() {
      if ($scope.loadBalancer.instances && $scope.loadBalancer.instances.length) {
        return;
      }

      var taskMonitor = {
        application: app,
        title: 'Deleting ' + loadBalancer.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        loadBalancer.providerType = $scope.loadBalancer.type;
        let vpcId = angular.isDefined($scope.loadBalancer.elb) ? $scope.loadBalancer.elb.vpcid : loadBalancer.vpcId || null;
        return loadBalancerWriter.deleteLoadBalancer(loadBalancer, app, { vpcId: vpcId });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        destructive: true,
        provider: 'aws',
        account: loadBalancer.accountId,
        applicationName: app.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
