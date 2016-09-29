'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.applySourceServerGroupCapacityDetails.controller', [])
  .controller('applySourceServerGroupCapacityDetailsCtrl', function($scope, $stateParams, executionDetailsSectionService) {
    $scope.configSections = ['capacitySnapshot', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
      $scope.parentDeployStage = _.find($scope.execution.stages, function(stage) {
        return stage.id === $scope.stage.parentStageId;
      });
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);
  });
