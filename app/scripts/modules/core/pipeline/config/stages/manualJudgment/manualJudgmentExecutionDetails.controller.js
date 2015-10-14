'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.manualJudgment.executionDetails.controller', [
  require('angular-ui-router'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('ManualJudgmentExecutionDetailsCtrl', function ($scope, $stateParams, $http, settings, executionDetailsSectionService, _) {
    $scope.configSections = ['manualJudgment', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();
    $scope.$on('$stateChangeSuccess', initialize, true);

    function provideJudgment(judgmentStatus, executionStatus) {
      var targetUrl = [settings.gateUrl, 'pipelines', $stateParams.executionId, 'stages', $scope.stage.id].join('/');
      $http({
        method: 'PATCH',
        url: targetUrl,
        data: angular.toJson({judgmentStatus: judgmentStatus})
      }).success(function() {
        $scope.stage.context.judgmentStatus = judgmentStatus;
        $scope.stage.status = executionStatus;

        var stageSummary = _.find($scope.execution.stageSummaries, function (stageSummary) {
          return stageSummary.id === $scope.stage.id;
        });
        if (stageSummary) {
          stageSummary.status = $scope.stage.status;
        }
      });
    }

    this.continue = function () {
      provideJudgment('continue', 'SUCCEEDED');
    };

    this.stop = function () {
      provideJudgment('stop', 'TERMINAL');
    };
  }).name;
