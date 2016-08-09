'use strict';

let angular = require('angular');

/*
  This stage is just here so that we can experiment with baking Docker containers within pipelines.
  Without this stage, programmatically-created pipelines with Docker bake stages would not render
  execution details.
 */
module.exports = angular.module('spinnaker.core.pipeline.stage.docker.bakeStage', [
  require('../../../../core/utils/lodash.js'),
  require('../../../../core/pipeline/config/pipelineConfigProvider.js'),
  require('./bakeExecutionDetails.controller.js'),
  require('./../../../../core/pipeline/config/stages/bake/bakery.service.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'bake',
      cloudProvider: 'docker',
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      executionLabelTemplateUrl: require('../../../../core/pipeline/config/stages/bake/bakeExecutionLabel.html'),
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        { type: 'requiredField', fieldName: 'package', },
      ],
      restartable: true,
    });
  })
  .controller('dockerBakeStageCtrl', function($scope, bakeryService, $q, _, authenticationService) {

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
        baseOsOptions: bakeryService.getBaseOsOptions('docker'),
        baseLabelOptions: bakeryService.getBaseLabelOptions(),
      }).then(function(results) {
        $scope.baseOsOptions = results.baseOsOptions.baseImages;
        $scope.baseLabelOptions = results.baseLabelOptions;

        if (!$scope.stage.baseOs && $scope.baseOsOptions && $scope.baseOsOptions.length) {
          $scope.stage.baseOs = $scope.baseOsOptions[0].id;
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
  });
