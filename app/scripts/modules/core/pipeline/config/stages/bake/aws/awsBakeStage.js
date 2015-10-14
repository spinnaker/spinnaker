'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.aws.bakeStage', [
  require('../../../../../utils/lodash.js'),
  require('../../../pipelineConfigProvider.js'),
  require('./bakeExecutionDetails.controller.js'),
  require('../bakery.service.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'bake',
      cloudProvider: 'aws',
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        { type: 'requiredField', fieldName: 'package', },
        { type: 'requiredField', fieldName: 'regions', }
      ],
    });
  })
  .controller('awsBakeStageCtrl', function($scope, bakeryService, $q, _, authenticationService) {

    var stage = $scope.stage;

    stage.regions = stage.regions || [];

    if (!$scope.stage.user) {
      $scope.stage.user = authenticationService.getAuthenticatedUser().name;
    }

    $scope.viewState = {
      loading: true,
    };

    function initialize() {
      $q.all({
        regions: bakeryService.getRegions('aws'),
        baseOsOptions: bakeryService.getBaseOsOptions(),
        baseLabelOptions: bakeryService.getBaseLabelOptions(),
        vmTypes: bakeryService.getVmTypes(),
        storeTypes: bakeryService.getStoreTypes(),
      }).then(function(results) {
        $scope.regions = results.regions;
        $scope.vmTypes = results.vmTypes;
        if (!$scope.stage.vmType && $scope.vmTypes && $scope.vmTypes.length) {
          $scope.stage.vmType = $scope.vmTypes[0];
        }
        $scope.storeTypes = results.storeTypes;
        if (!$scope.stage.storeType && $scope.storeTypes && $scope.storeTypes.length) {
          $scope.stage.storeType = $scope.storeTypes[0];
        }
        if ($scope.regions.length === 1) {
          $scope.stage.region = $scope.regions[0];
        } else if ($scope.regions.indexOf($scope.stage.region) === -1) {
          delete $scope.stage.region;
        }
        if (!$scope.stage.regions.length && $scope.application.defaultRegion) {
          $scope.stage.regions.push($scope.application.defaultRegion);
        }
        if (!$scope.stage.regions.length && $scope.application.defaultRegion) {
          $scope.stage.regions.push($scope.application.defaultRegion);
        }
        $scope.baseOsOptions = results.baseOsOptions;
        $scope.baseLabelOptions = results.baseLabelOptions;

        if (!$scope.stage.baseOs && $scope.baseOsOptions && $scope.baseOsOptions.length) {
          $scope.stage.baseOs = $scope.baseOsOptions[0];
        }
        if (!$scope.stage.baseLabel && $scope.baseLabelOptions && $scope.baseLabelOptions.length) {
          $scope.stage.baseLabel = $scope.baseLabelOptions[0];
        }
        $scope.viewState.loading = false;
      });
    }

    function deleteEmptyProperties() {
      _.forOwn($scope.stage, function(val, key) {
        if (val === '') {
          delete $scope.stage[key];
        }
      });
    }

    $scope.$watch('stage', deleteEmptyProperties, true);

    initialize();
  }).name;
