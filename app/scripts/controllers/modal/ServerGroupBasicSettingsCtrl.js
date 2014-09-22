'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ServerGroupBasicSettingsCtrl', function($scope, modalWizardService, $, _) {

    var populateRegions = function() {
      $scope.regions = $scope.regionsKeyedByAccount[$scope.command.credentials].regions;
    };
    populateRegions();

    var populateRegionalAvailabilityZones = function() {
      $scope.regionalAvailabilityZones = _.find($scope.regionsKeyedByAccount[$scope.command.credentials].regions, {'name': $scope.command.region}).availabilityZones;
    };
    populateRegionalAvailabilityZones();

    var populateRegionalImages = function() {
      $scope.images = _($scope.packageImages)
        .filter({'region': $scope.command.region})
        .valueOf();
    };
    populateRegionalImages();

    var populateRegionalSubnetPurposes = function() {
      $scope.regionSubnetPurposes = _($scope.subnets)
        .filter({'account': $scope.command.credentials, 'region': $scope.command.region, 'target': 'ec2'})
        .pluck('purpose')
        .uniq()
        .union([''])
        .valueOf();
    };
    populateRegionalSubnetPurposes();

    $scope.$watch('command.credentials', function () {
      populateRegions();
      onRegionChange();
    });

    $scope.$watch('command.region', function () {
      onRegionChange();
      populateRegionalImages();
    });

    $scope.$watch('command.subnetType', function (oldVal, newVal) {
      if (newVal) {
        var subnet = _($scope.subnets)
          .find({'purpose': $scope.command.subnetType, 'availabilityZone': $scope.command.availabilityZones[0]});
        $scope.command.vpcId = subnet ? subnet.vpcId : null;
      }
    });

    var onRegionChange = function() {
      populateRegionalAvailabilityZones();
      populateRegionalSubnetPurposes();
    };

  });
