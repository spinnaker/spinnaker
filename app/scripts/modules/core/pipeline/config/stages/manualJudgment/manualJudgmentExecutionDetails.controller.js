'use strict';

let angular = require('angular');

module.exports = angular
    .module('spinnaker.core.pipeline.stage.manualJudgment.executionDetails.controller', [
    require('angular-ui-router'),
    require('./manualJudgment.service.js'),
    require('../../../../delivery/details/executionDetailsSection.service.js'),
    require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  ])
  .controller('ManualJudgmentExecutionDetailsCtrl', function ($scope, $stateParams, manualJudgmentService,
                                                              executionDetailsSectionService) {

    $scope.configSections = ['manualJudgment', 'taskStatus'];
    $scope.viewState = {
      submitting: false,
      judgmentDecision: null,
      judgmentInput: null,
      error: false,
    };

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();
    $scope.$on('$stateChangeSuccess', initialize, true);

    function judgmentMade() {
      // do not update the submitting state - the reload of the executions will clear it out; otherwise,
      // there is a flash on the screen when we go from submitting to not submitting to the buttons not being there.
      $scope.application.reloadExecutions();
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
  });
