'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.applySourceServerGroupCapacityDetails.controller', [])
  .controller('applySourceServerGroupCapacityDetailsCtrl', function($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['capacitySnapshot', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
      $scope.parentDeployStage = _.find($scope.execution.stages, (stage) => stage.id === $scope.stage.parentStageId);
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
