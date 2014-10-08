'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('LoadBalancerDetailsCtrl', function ($scope, $state, $exceptionHandler, notifications, loadBalancer, application, securityGroupService, $modal, _, confirmationModalService, orcaService) {

    function extractLoadBalancer() {
      $scope.loadBalancer = application.loadBalancers.filter(function (test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId;
      })[0];

      if ($scope.loadBalancer && $scope.loadBalancer.elb && $scope.loadBalancer.elb.securityGroups) {
        var securityGroups = [];
        $scope.loadBalancer.elb.securityGroups.forEach(function (securityGroupId) {
          var match = securityGroupService.getApplicationSecurityGroup(application, loadBalancer.accountId, loadBalancer.region, securityGroupId);
          if (match) {
            securityGroups.push(match);
          }
        });
        $scope.securityGroups = _.sortBy(securityGroups, 'name');
      }
      if (!$scope.loadBalancer) {
        notifications.create({
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
        templateUrl: 'views/application/modal/loadBalancer/editLoadBalancer.html',
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
        return orcaService.deleteLoadBalancer(loadBalancer, application.name);
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + loadBalancer.name + '?',
        buttonText: 'Delete ' + loadBalancer.name,
        destructive: true,
        account: loadBalancer.account,
        applicationName: application.name,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

  }
);
