'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ServerGroupSecurityGroupsCtrl', function($scope, _) {

    var populateRegionalSecurityGroups = function() {
      $scope.regionalSecurityGroups = _($scope.securityGroups[$scope.command.credentials].aws[$scope.command.region])
        .filter({'vpcId': $scope.command.vpcId || null})
        .pluck('name')
        .valueOf();
    };
    populateRegionalSecurityGroups();

    this.removeSecurityGroup = function(index) {
      $scope.command.securityGroups.splice(index, 1);
    };

    this.addSecurityGroup = function() {
      if (!$scope.command.securityGroups || !$scope.command.securityGroups.length) {
        $scope.command.securityGroups = [];
      }
      $scope.command.securityGroups.push('');
    };

  });
