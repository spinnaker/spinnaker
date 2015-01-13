'use strict';


angular.module('deckApp')
  .controller('LoadBalancerDetailsCtrl', function ($scope, $state, $exceptionHandler, notificationsService, loadBalancer, application,
                                                   securityGroupService, $modal, _, confirmationModalService, loadBalancerWriter, loadBalancerService, loadBalancerReader) {

    $scope.state = {
      loading: true
    };

    function extractLoadBalancer() {
      $scope.loadBalancer = application.loadBalancers.filter(function (test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId && test.vpcId === loadBalancer.vpcId;
      })[0];

      if ($scope.loadBalancer) {
        var detailsLoader = loadBalancerReader.getLoadBalancerDetails($scope.loadBalancer.type, loadBalancer.accountId, loadBalancer.region, loadBalancer.name);
        detailsLoader.then(function(details) {
          $scope.state.loading = false;
          var securityGroups = [];
          var filtered = details.filter(function(test) {
            return test.vpcid === loadBalancer.vpcId || (!test.vpcid && !loadBalancer.vpcId);
          });
          if (filtered.length) {
            $scope.loadBalancer.elb = filtered[0];
            $scope.loadBalancer.account = loadBalancer.accountId;
            $scope.loadBalancer.elb.securityGroups.forEach(function (securityGroupId) {
              var match = securityGroupService.getApplicationSecurityGroup(application, loadBalancer.accountId, loadBalancer.region, securityGroupId);
              if (match) {
                securityGroups.push(match);
              }
            });
            $scope.securityGroups = _.sortBy(securityGroups, 'name');
          }
        });
      }
      if (!$scope.loadBalancer) {
        notificationsService.create({
          message: 'No load balancer named "' + loadBalancer.name + '" was found in ' + loadBalancer.accountId + ':' + loadBalancer.region,
          autoDismiss: true,
          hideTimestamp: true,
          strong: true
        });
        $state.go('^');
      }
    }

    extractLoadBalancer();

    application.registerAutoRefreshHandler(extractLoadBalancer, $scope);

    this.editLoadBalancer = function editLoadBalancer() {
      $modal.open({
        templateUrl: 'scripts/modules/loadBalancers/editLoadBalancer.html',
        controller: 'CreateLoadBalancerCtrl as ctrl',
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
        loadBalancer.providerType = $scope.loadBalancer.type;
        return loadBalancerWriter.deleteLoadBalancer(loadBalancer, application);
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        destructive: true,
        account: loadBalancer.accountId,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
