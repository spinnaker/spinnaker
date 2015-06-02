'use strict';

angular.module('spinnaker.pipelines.stage.manualJudgement.executionDetails.controller', [
  'ui.router',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
])
  .controller('ManualJudgementExecutionDetailsCtrl', function ($scope, $stateParams, $http, settings, executionDetailsSectionService) {
    $scope.configSections = ['manualJudgement', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;
    }

    initialize();
    $scope.$on('$stateChangeSuccess', initialize, true);

    function provideJudgement(judgementStatus, executionStatus) {
      var targetUrl = [settings.gateUrl, 'pipelines', $stateParams.executionId, 'stages', $scope.stage.id].join('/');
      $http({
        method: 'PATCH',
        url: targetUrl,
        data: angular.toJson({judgementStatus: judgementStatus})
      }).success(function() {
        $scope.stage.context.judgementStatus = judgementStatus;
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
      provideJudgement('continue', 'SUCCEEDED');
    };

    this.stop = function () {
      provideJudgement('stop', 'TERMINAL');
    };
  });
