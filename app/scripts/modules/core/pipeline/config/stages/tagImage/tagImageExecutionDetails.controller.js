'use strict';

import detailsSectionModule from 'core/delivery/details/executionDetailsSection.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.tagImage.executionDetails.controller', [
    require('angular-ui-router'),
    detailsSectionModule,
    require('core/delivery/details/executionDetailsSectionNav.directive.js'),
  ])
  .controller('TagImageExecutionDetailsCtrl', function ($scope, $stateParams, manualJudgmentService,
                                                        executionDetailsSectionService) {

    $scope.configSections = ['tagImageConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);

  });
