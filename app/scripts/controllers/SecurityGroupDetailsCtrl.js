'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('SecurityGroupDetailsCtrl', function ($scope, $rootScope, securityGroup, application, securityGroupService) {

    $scope.loading = true;

    securityGroupService.getSecurityGroup(securityGroup.accountId, securityGroup.region, securityGroup.name).then(function(details) {
      $scope.loading = false;
      $scope.securityGroup = details;
      if (details && details.inboundRules) {
        $scope.ipRangeRules = details.inboundRules.filter(function(rule) {
          return rule.range;
        });
        $scope.securityGroupRules = details.inboundRules.filter(function(rule) {
          return rule.securityGroup;
        });
        $scope.securityGroupRules.forEach(function(inboundRule) {
          if (!inboundRule.securityGroup.name) {
            inboundRule.securityGroup.name = securityGroupService.getSecurityGroupFromIndex(application, details.accountName, details.region, inboundRule.securityGroup.id).name;
          }
        });
      }
    });
  }
);
