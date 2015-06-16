'use strict';


angular.module('spinnaker.loadBalancer.controller')
  .controller('LoadBalancerCtrl', function ($scope, $state, application, loadBalancer) {
    $scope.application = application;

    function extractLoadBalancer() {
      $scope.loadBalancer = application.loadBalancers.filter(function (test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.account;
      })[0];

      if (!$scope.loadBalancer) {
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
