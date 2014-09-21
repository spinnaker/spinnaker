'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('LoadBalancerDetailsCtrl', function ($scope, $rootScope, loadBalancer, application, securityGroupService, $modal) {

    $scope.loadBalancer = application.loadBalancers.filter(function (test) {
      return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId;
    })[0];

    this.editLoadBalancer = function editLoadBalancer() {
      $modal.open({
        templateUrl: 'views/application/modal/loadBalancer/editLoadBalancer.html',
        controller: 'CreateLoadBalancerCtrl as ctrl',
        resolve: {
          application: function() { return application; },
          loadBalancer: function() { return angular.copy($scope.loadBalancer); }
        }
      });
    };

  }
);
