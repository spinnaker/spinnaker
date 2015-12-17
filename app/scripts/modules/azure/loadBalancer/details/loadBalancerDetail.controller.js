'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.loadBalancer.details.controller', [
  require('angular-ui-router'),
  require('../../../core/securityGroup/securityGroup.read.service.js'),
  require('../loadBalancer.write.service.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('../../../core/presentation/collapsibleSection/collapsibleSection.directive.js'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
])
  .controller('azureLoadBalancerDetailsCtrl', function ($scope, $state, $exceptionHandler, $modal, loadBalancer, app, InsightFilterStateModel,
                                                   securityGroupReader, _, confirmationModalService, azureLoadBalancerWriter, loadBalancerReader, $q) {

    $scope.state = {
      loading: true
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractLoadBalancer() {

      $scope.loadBalancer = app.loadBalancers.filter(function (test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId;
      })[0];

      if ($scope.loadBalancer) {
        var detailsLoader = loadBalancerReader.getLoadBalancerDetails($scope.loadBalancer.provider, loadBalancer.accountId, loadBalancer.region, loadBalancer.name);

        return detailsLoader.then(function(details) {
          $scope.state.loading = false;
          var securityGroups = [];

          var filtered = details.filter(function(test) {
            return test.name === loadBalancer.name;
          });

          if (filtered.length) {
            $scope.loadBalancer.elb = filtered[0];

            $scope.loadBalancer.account = loadBalancer.accountId;

            if($scope.loadBalancer.elb.securityGroups){
              $scope.loadBalancer.elb.securityGroups.forEach(function (securityGroupId) {
                var match = securityGroupReader.getApplicationSecurityGroup(app, loadBalancer.accountId, loadBalancer.region, securityGroupId);
                if (match) {
                  securityGroups.push(match);
                }
              });
              $scope.securityGroups = _.sortBy(securityGroups, 'name');
            }
          }
        });
      }
      if (!$scope.loadBalancer) {
        $state.go('^');
      }

      return $q.when(null);
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
      $modal.open({
        templateUrl: require('../configure/editLoadBalancer.html'),
        controller: 'azureCreateLoadBalancerCtrl as ctrl',
        resolve: {
          application: function() { return app; },
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
        application: app,
        title: 'Deleting ' + loadBalancer.name,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true
      };

      var submitMethod = function () {
        loadBalancer.providerType = $scope.loadBalancer.type;
        let vpcId = angular.isDefined($scope.loadBalancer.elb) ? $scope.loadBalancer.elb.vpcid : loadBalancer.vpcId || null;
        return azureLoadBalancerWriter.deleteLoadBalancer(loadBalancer, app, { vpcId: vpcId });
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        destructive: true,
        provider: 'azure',
        account: loadBalancer.accountId,
        applicationName: app.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
