'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ServerGroupSecurityGroupsCtrl', function($scope, _) {

    var populateRegionalSecurityGroups = function() {
      $scope.regionalSecurityGroups = _($scope.securityGroups[$scope.command.credentials].aws[$scope.command.region])
        .filter({'vpcId': $scope.command.vpcId || null})
        .valueOf();
    };
    populateRegionalSecurityGroups();

  });
