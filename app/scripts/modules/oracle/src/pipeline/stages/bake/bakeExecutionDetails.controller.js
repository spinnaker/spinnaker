'use strict';

import { EXECUTION_DETAILS_SECTION_SERVICE, SETTINGS } from '@spinnaker/core';

const angular = require('angular');

export const ORACLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER =
  'spinnaker.oracle.pipeline.stage.bake.executionDetails.controller';
export const name = ORACLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER; // for backwards compatibility
angular
  .module(ORACLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER, [
    EXECUTION_DETAILS_SECTION_SERVICE,
    require('@uirouter/angularjs').default,
  ])
  .controller('oracleBakeExecutionDetailsCtrl', [
    '$log',
    '$scope',
    '$stateParams',
    'executionDetailsSectionService',
    '$interpolate',
    function($log, $scope, $stateParams, executionDetailsSectionService, $interpolate) {
      $scope.configSections = ['bakeConfig', 'taskStatus'];

      let initialized = () => {
        $scope.detailsSection = $stateParams.details;
        $scope.provider = $scope.stage.context.cloudProviderType || 'oracle';
        $scope.roscoMode = SETTINGS.feature.roscoMode;
        $scope.bakeryDetailUrl = $interpolate(
          $scope.roscoMode && SETTINGS.roscoDetailUrl ? SETTINGS.roscoDetailUrl : SETTINGS.bakeryDetailUrl,
        );
      };

      let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

      initialize();

      $scope.$on('$stateChangeSuccess', initialize);
    },
  ]);
