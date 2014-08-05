'use strict';

angular.module('deckApp')
  .controller('LoadBalancersNavCtrl', function($scope, application) {

    $scope.application = application;
    $scope.loadBalancersLoaded = false;
    $scope.loadBalancers = [];

    $scope.getLoadBalancersForAccount = function(accountName) {
      return $scope.loadBalancers.filter(function(loadBalancer) {
        return loadBalancer.account === accountName;
      });
    };

    application.getLoadBalancers().then(function(loadBalancers) {
      $scope.loadBalancers = loadBalancers;
      $scope.loadBalancersLoaded = true;
    });
  });
