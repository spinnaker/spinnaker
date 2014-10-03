'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('LoadBalancerCtrl', function ($scope, $state, notifications, application, loadBalancer) {
    $scope.application = application;

    function extractLoadBalancer() {
      $scope.loadBalancer = application.loadBalancers.filter(function (test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account.name === loadBalancer.account.name;
      })[0];

      if (!$scope.loadBalancer) {
        notifications.create({
          message: 'No load balancer named "' + loadBalancer.name + '" was found in ' + loadBalancer.account + ':' + loadBalancer.region,
          autoDismiss: true,
          hideTimestamp: true,
          strong: true
        });
        $state.go('^');
      }
    }

    extractLoadBalancer();

    application.registerAutoRefreshHandler(extractLoadBalancer, $scope);

    $scope.displayOptions = {
      limitInstanceDisplay: false,
      showServerGroups: true,
      showInstances: true
    };

  }
);
