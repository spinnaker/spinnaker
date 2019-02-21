'use strict';

import _ from 'lodash';
import { EXECUTION_DETAILS_SECTION_SERVICE } from 'core/pipeline/details/executionDetailsSection.service';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.pipeline.executionDetails.controller', [
    require('@uirouter/angularjs').default,
    EXECUTION_DETAILS_SECTION_SERVICE,
  ])
  .controller('pipelineExecutionDetailsCtrl', ['$scope', '$stateParams', 'executionDetailsSectionService', function($scope, $stateParams, executionDetailsSectionService) {
    $scope.configSections = ['pipelineConfig', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
    };

    if (_.has($scope.stage, 'context.pipelineParameters')) {
      $scope.configSections = ['pipelineConfig', 'parameters', 'taskStatus'];
      $scope.parameters = Object.keys($scope.stage.context.pipelineParameters)
        .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()))
        .map(key => {
          return { key: key, val: $scope.stage.context.pipelineParameters[key] };
        });
    }

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
  }]);
