'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('SecurityGroupDetailsCtrl', function ($scope, $rootScope, securityGroup, application, securityGroupService) {

    var details = application.securityGroups.filter(function (test) {
      return test.name === securityGroup.name && test.region === securityGroup.region && test.account === securityGroup.accountName;
    })[0];

    $scope.securityGroup = details;
    if (details && details.inboundRules) {
      details.inboundRules.forEach(function(inboundRule) {
        if (!inboundRule.securityGroup.name) {
          inboundRule.securityGroup.name = securityGroupService.getSecurityGroup(application, details.accountName, details.region, inboundRule.securityGroup.id).name;
        }
      });
    }

  }
);
