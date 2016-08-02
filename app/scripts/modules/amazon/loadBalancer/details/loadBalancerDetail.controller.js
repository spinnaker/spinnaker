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
  require('../../../core/subnet/subnet.read.service'),
])
  .controller('awsLoadBalancerDetailsCtrl', function ($scope, $state, $uibModal, $q, loadBalancer, app, InsightFilterStateModel,
                                                   securityGroupReader, _, confirmationModalService, loadBalancerWriter,
                                                      loadBalancerReader, subnetReader) {

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractLoadBalancer() {
      let [appLoadBalancer] = app.loadBalancers.data.filter(function (test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId;
      });

      if (appLoadBalancer) {
        var detailsLoader = loadBalancerReader.getLoadBalancerDetails('aws', loadBalancer.accountId, loadBalancer.region, loadBalancer.name);
        return detailsLoader.then(function(details) {
          $scope.loadBalancer = appLoadBalancer;
          $scope.state.loading = false;
          var securityGroups = [];
          if (details.length) {
            $scope.loadBalancer.elb = details[0];
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

            if ($scope.loadBalancer.subnets) {
              $scope.loadBalancer.subnetDetails = $scope.loadBalancer.subnets.reduce( (detailList, subnetId) => {
                subnetReader.getSubnetByIdAndProvider(subnetId, $scope.loadBalancer.provider)
                  .then( (subnetDetail) => {
                    detailList.push(subnetDetail);
                  });

                return detailList;
              }, []);
            }
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

    app.loadBalancers.ready().then(extractLoadBalancer).then(() => {
      // If the user navigates away from the view before the initial extractLoadBalancer call completes,
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed) {
        app.loadBalancers.onRefresh($scope, extractLoadBalancer);
      }
    });

    this.getFirstSubnetPurpose = function(subnetDetailsList = []) {
      return _.first(subnetDetailsList.map(subnet => subnet.purpose)) || '';
    };

    this.editLoadBalancer = function editLoadBalancer() {
      $uibModal.open({
        templateUrl: require('../configure/editLoadBalancer.html'),
        controller: 'awsCreateLoadBalancerCtrl as ctrl',
        size: 'lg',
        resolve: {
          application: function() { return app; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); },
          isNew: function() { return false; },
          forPipelineConfig: function() { return false; }
        }
      });
    };

    this.cloneLoadBalancer = function () {
      $uibModal.open({
        templateUrl: require('../configure/createLoadBalancer.html'),
        controller: 'awsCreateLoadBalancerCtrl as ctrl',
        size: 'lg',
        resolve: {
          application: function() { return app; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); },
          isNew: function() { return true; },
          forPipelineConfig: function() { return false; }
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
        provider: 'aws',
        account: loadBalancer.accountId,
        applicationName: app.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
