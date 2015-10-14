'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.gce.bakeStage', [
  require('../../../../../utils/lodash.js'),
  require('../../../pipelineConfigProvider.js'),
  require('./bakeExecutionDetails.controller.js'),
  require('./../bakery.service.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'bake',
      cloudProvider: 'gce',
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        { type: 'requiredField', fieldName: 'package', },
      ],
    });
  })
  .controller('gceBakeStageCtrl', function($scope, bakeryService, $q, _, authenticationService) {

    var stage = $scope.stage;

    stage.region = 'global';

    if (!$scope.stage.user) {
      $scope.stage.user = authenticationService.getAuthenticatedUser().name;
    }

    $scope.viewState = {
      loading: true,
    };

    function initialize() {
      $scope.viewState.providerSelected = true;
      $q.all({
        baseOsOptions: bakeryService.getBaseOsOptions(),
        baseLabelOptions: bakeryService.getBaseLabelOptions(),
      }).then(function(results) {
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
