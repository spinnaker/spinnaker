'use strict';

module.exports = function($scope, application) {

  $scope.application = application;
  $scope.loadBalancers = application.loadBalancers;

  $scope.getLoadBalancersForAccount = function(account) {
    return $scope.loadBalancers.filter(function(loadBalancer) {
      return loadBalancer.account === account;
    });
  };

};
