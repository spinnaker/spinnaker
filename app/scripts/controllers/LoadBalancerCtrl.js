'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('LoadBalancerCtrl', function ($scope, application, loadBalancer) {
    $scope.application = application;

    $scope.loadBalancer = application.loadBalancers.filter(function (test) {
      return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account.name === loadBalancer.account.name;
    })[0];

    $scope.displayOptions = {
      limitInstanceDisplay: false,
      showServerGroups: true,
      showInstances: true
    };

  }
);
