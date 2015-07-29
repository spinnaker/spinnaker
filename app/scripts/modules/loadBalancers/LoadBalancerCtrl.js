'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.controller.loadBalancerCtrl', [
])
  .controller('LoadBalancerCtrl', function ($scope, $state, app, loadBalancer) {
    $scope.application = app;

    function extractLoadBalancer() {
      $scope.loadBalancer = app.loadBalancers.filter(function (test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.account;
      })[0];

      if (!$scope.loadBalancer) {
        $state.go('^');
      }
    }

    extractLoadBalancer();

    app.registerAutoRefreshHandler(extractLoadBalancer, $scope);

    $scope.displayOptions = {
      limitInstanceDisplay: false,
      showServerGroups: true,
      showInstances: true
    };

  }
).name;
