'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('LoadBalancersNavCtrl', function ($scope, application) {

    $scope.application = application;
    $scope.loadBalancers = application.loadBalancers;

    $scope.getLoadBalancersForAccount = function (account) {
      return $scope.loadBalancers.filter(function (loadBalancer) {
        return loadBalancer.account === account;
      });
    };

  }
);
