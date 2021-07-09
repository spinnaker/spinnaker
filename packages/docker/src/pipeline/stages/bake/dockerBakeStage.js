'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AuthenticationService, BakeExecutionLabel, BakeryReader, Registry } from '@spinnaker/core';
import { DOCKER_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER } from './bakeExecutionDetails.controller';

/*
  This stage is just here so that we can experiment with baking Docker containers within pipelines.
  Without this stage, programmatically-created pipelines with Docker bake stages would not render
  execution details.
 */
export const DOCKER_PIPELINE_STAGES_BAKE_DOCKERBAKESTAGE = 'spinnaker.docker.pipeline.stage.bakeStage';
export const name = DOCKER_PIPELINE_STAGES_BAKE_DOCKERBAKESTAGE; // for backwards compatibility
module(DOCKER_PIPELINE_STAGES_BAKE_DOCKERBAKESTAGE, [DOCKER_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'bake',
      cloudProvider: 'docker',
      label: 'Bake',
      description: 'Bakes an image',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      executionLabelComponent: BakeExecutionLabel,
      extraLabelLines: (stage) => {
        return stage.masterStage.context.allPreviouslyBaked || stage.masterStage.context.somePreviouslyBaked ? 1 : 0;
      },
      supportsCustomTimeout: true,
      validators: [{ type: 'requiredField', fieldName: 'package' }],
      restartable: true,
    });
  })
  .controller('dockerBakeStageCtrl', [
    '$scope',
    '$q',
    function ($scope, $q) {
      const stage = $scope.stage;

      stage.region = 'global';

      if (!$scope.stage.user) {
        $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
      }

      $scope.viewState = {
        loading: true,
      };

      function initialize() {
        $scope.viewState.providerSelected = true;
        $q.all([BakeryReader.getBaseOsOptions('docker'), BakeryReader.getBaseLabelOptions()]).then(function ([
          baseOsOptions,
          baseLabelOptions,
        ]) {
          $scope.baseOsOptions = baseOsOptions.baseImages;
          $scope.baseLabelOptions = baseLabelOptions;

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
        _.forOwn($scope.stage, function (val, key) {
          if (val === '') {
            delete $scope.stage[key];
          }
        });
      }

      $scope.$watch('stage', deleteEmptyProperties, true);

      initialize();
    },
  ]);
