'use strict';

angular.module('deckApp.pipelines.stage.canary.summary.controller', [
  'ui.router',
  'deckApp.utils.lodash',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
  'deckApp.pipelines.stage.canary.actions.generate.score.controller',
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

  });
