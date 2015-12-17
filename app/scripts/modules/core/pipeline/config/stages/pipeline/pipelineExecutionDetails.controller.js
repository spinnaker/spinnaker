'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.pipeline.executionDetails.controller', [
  require('angular-ui-router'),
  require('../../../../utils/lodash.js'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('pipelineExecutionDetailsCtrl', function ($scope, $stateParams, executionDetailsSectionService, _) {

    $scope.configSections = ['pipelineConfig', 'taskStatus'];

    if (_.has($scope.stage, 'context.pipelineParameters')) {
      $scope.configSections = ['pipelineConfig', 'parameters', 'taskStatus'];
      $scope.parameters = Object.keys($scope.stage.context.pipelineParameters)
        .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()))
        .map((key) => {
          return { key: key, val: $scope.stage.context.pipelineParameters[key] };
        });
    }

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
