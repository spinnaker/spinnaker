'use strict';

angular.module('spinnaker.pipelines.stage.canary.summary.controller', [
  'ui.router',
  'spinnaker.utils.lodash',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
  'spinnaker.pipelines.stage.canary.actions.generate.score.controller',
  'spinnaker.pipelines.stage.canary.actions.override.result.controller',
])
  .controller('CanaryExecutionSummaryCtrl', function ($scope, $http, settings, $modal) {

    this.generateCanaryScore = function() {
      $modal.open({
        templateUrl: 'scripts/modules/pipelines/config/stages/canary/actions/generateScore.modal.html',
        controller: 'GenerateScoreCtrl as ctrl',
        resolve: {
          canaryId: function() { return $scope.stageSummary.masterStage.context.canary.id; },
        },
      });
    };

    this.endCanary = function() {
      $modal.open({
        templateUrl: 'scripts/modules/pipelines/config/stages/canary/actions/endCanary.modal.html',
        controller: 'EndCanaryCtrl as ctrl',
        resolve: {
          canaryId: function() { return $scope.stageSummary.masterStage.context.canary.id; },
        },
      });
    };

  });
