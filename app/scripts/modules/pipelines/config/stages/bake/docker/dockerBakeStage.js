/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

let angular = require('angular');

/*
  This stage is just here so that we can experiment with baking Docker containers within pipelines.
  Without this stage, programmatically-created pipelines with Docker bake stages would not render
  execution details.
 */
module.exports = angular.module('spinnaker.pipelines.stage.docker.bakeStage', [
  require('../../../../../utils/lodash.js'),
  require('../../../pipelineConfigProvider.js'),
  require('./bakeExecutionDetails.controller.js'),
  require('./../bakery.service.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'bake',
      cloudProvider: 'docker',
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        {
          type: 'requiredField',
          fieldName: 'package',
          message: '<strong>Package</strong> is a required field on bake stages.',
        },
      ],
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
