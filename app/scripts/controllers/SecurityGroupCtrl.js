'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('SecurityGroupCtrl', function($scope, securityGroup, application, securityGroupService) {

    $scope.displayOptions = {
      showServerGroups: true,
      showLoadBalancers: true
    };

    $scope.account = securityGroup.account;
    $scope.region = securityGroup.region;
    $scope.securityGroup = securityGroupService.getSecurityGroupFromIndex(application, securityGroup.account, securityGroup.region, securityGroup.name);

    $scope.sortFilter = {
      allowSorting: false
    };

  }
);
