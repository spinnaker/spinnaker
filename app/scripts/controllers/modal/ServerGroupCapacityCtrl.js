'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ServerGroupCapacityCtrl', function($scope, _) {

    var populateRegionalAvailabilityZones = function() {
      $scope.regionalAvailabilityZones = _.find($scope.regionsKeyedByAccount[$scope.command.credentials].regions, {'name': $scope.command.region}).availabilityZones;
    };
    populateRegionalAvailabilityZones();

  });
