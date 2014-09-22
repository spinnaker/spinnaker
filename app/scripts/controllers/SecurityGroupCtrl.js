'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('SecurityGroupCtrl', function($scope, securityGroup, application, securityGroupService, $modal) {

    $scope.displayOptions = {
      showServerGroups: true,
      showLoadBalancers: true
    };

    $scope.account = securityGroup.account;
    $scope.region = securityGroup.region;
    $scope.securityGroup = securityGroupService.getApplicationSecurityGroup(application, securityGroup.account, securityGroup.region, securityGroup.name);

    $scope.sortFilter = {
      allowSorting: false
    };

    this.editInboundRules = function editInboundRules() {
      $modal.open({
        templateUrl: 'views/application/modal/securityGroup/createSecurityGroupPage2.html',
        controller: 'EditSecurityGroupCtrl as ctrl',
        resolve: {
          securityGroup: function() {
            return securityGroupService.getSecurityGroupDetails(application, securityGroup.account, securityGroup.region, securityGroup.name);
          },
          applicationName: function() { return application.name; }
        }
      });
    };
  }
);
