'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('LoadBalancerDetailsCtrl', function ($scope, $rootScope, loadBalancer, application, securityGroupService, $modal, _) {

    $scope.loadBalancer = application.loadBalancers.filter(function (test) {
      return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId;
    })[0];

    if ($scope.loadBalancer && $scope.loadBalancer.elb && $scope.loadBalancer.elb.securityGroups) {
      var securityGroups = [];
      $scope.loadBalancer.elb.securityGroups.forEach(function(securityGroupId) {
        var match = securityGroupService.getApplicationSecurityGroup(application, loadBalancer.accountId, loadBalancer.region, securityGroupId);
        if (match) {
          securityGroups.push(match);
        }
      });
      $scope.securityGroups = _.sortBy(securityGroups, 'name');
    }

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
