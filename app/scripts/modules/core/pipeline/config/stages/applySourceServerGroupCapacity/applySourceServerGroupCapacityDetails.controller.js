'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.applySourceServerGroupCapacityDetails.controller', [
  require('../../../../utils/lodash.js'),
  ])
  .controller('applySourceServerGroupCapacityDetailsCtrl', function($scope, $stateParams, executionDetailsSectionService, _) {
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
