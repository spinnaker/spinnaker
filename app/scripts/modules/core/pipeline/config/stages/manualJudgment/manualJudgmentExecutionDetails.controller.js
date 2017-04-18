'use strict';

const angular = require('angular');

import {EXECUTION_DETAILS_SECTION_SERVICE} from 'core/delivery/details/executionDetailsSection.service';
import {MANUAL_JUDGMENT_COMPONENT} from './manualJudgment.component';

module.exports = angular
    .module('spinnaker.core.pipeline.stage.manualJudgment.executionDetails.controller', [
    require('angular-ui-router'),
    MANUAL_JUDGMENT_COMPONENT,
    EXECUTION_DETAILS_SECTION_SERVICE,
    require('core/delivery/details/executionDetailsSectionNav.directive.js'),
  ])
  .controller('ManualJudgmentExecutionDetailsCtrl', function ($scope, $stateParams, manualJudgmentService,
                                                              executionDetailsSectionService) {

    $scope.configSections = ['manualJudgment', 'taskStatus'];

    let initialized = () => {
      $scope.detailsSection = $stateParams.details;
    };

    $scope.viewState = {
      submitting: false,
      judgmentDecision: null,
      judgmentInput: null,
      error: false,
    };

    function judgmentMade() {
      // do not update the submitting state - the reload of the executions will clear it out; otherwise,
      // there is a flash on the screen when we go from submitting to not submitting to the buttons not being there.
      $scope.application.executions.refresh();
    }

    function judgmentFailure() {
      $scope.viewState.submitting = false;
      $scope.viewState.error = true;
    }

    this.provideJudgment = (judgmentDecision, judgmentInput) => {
      $scope.viewState.submitting = true;
      $scope.viewState.error = false;
      $scope.viewState.judgmentDecision = judgmentDecision;
      $scope.viewState.judgmentInput = judgmentInput;
      return manualJudgmentService.provideJudgment($scope.execution, $scope.stage, judgmentDecision, judgmentInput)
        .then(judgmentMade, judgmentFailure);
    };

    let initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
  });
