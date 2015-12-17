'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.quickPatchAsg.bulkQuickPatchStage.executionDetails.controller', [
  require('angular-ui-router'),
  require('../../../../../core/delivery/details/executionDetailsSection.service.js'),
  require('../../../../../core/delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('BulkQuickPatchAsgExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService) {

    $scope.configSections = ['bulkQuickPatchStageConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
