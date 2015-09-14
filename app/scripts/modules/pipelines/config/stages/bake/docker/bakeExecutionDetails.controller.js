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

module.exports = angular.module('spinnaker.pipelines.stage.bake.docker.executionDetails.controller', [
  require('angular-ui-router'),
  require('../../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../../delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('dockerBakeExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, $timeout) {

    $scope.configSections = ['bakeConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update any scope values based on the stage
      $timeout(function() {
        $scope.provider = $scope.stage.context.cloudProviderType || 'aws';
      });
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  }).name;
