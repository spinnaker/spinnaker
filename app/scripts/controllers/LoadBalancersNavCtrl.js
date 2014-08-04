'use strict';

angular.module('deckApp')
  .controller('LoadBalancersNavCtrl', function($scope, application, _) {

    $scope.application = application;
    $scope.loadBalancersLoaded = false;
    $scope.loadBalancers = [];

    $scope.getLoadBalancersForAccount = function(accountName) {
      var loadBalancerNames = $scope.loadBalancers.map(function(loadBalancer) {
        if (loadBalancer.account === accountName) {
          return loadBalancer.name;
        }
      });
      return _.unique(loadBalancerNames);
    };

    application.getLoadBalancers().then(function(loadBalancers) {
      $scope.loadBalancers = loadBalancers;
      $scope.loadBalancersLoaded = true;
    });
  });
