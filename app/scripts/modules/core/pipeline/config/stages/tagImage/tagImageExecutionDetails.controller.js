'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.tagImage.executionDetails.controller', [
    require('angular-ui-router'),
    require('../../../../delivery/details/executionDetailsSection.service.js'),
    require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  ])
  .controller('TagImageExecutionDetailsCtrl', function ($scope, $stateParams, manualJudgmentService,
                                                        executionDetailsSectionService) {

    $scope.configSections = ['tagImageConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();
    $scope.$on('$stateChangeSuccess', initialize, true);
  });
