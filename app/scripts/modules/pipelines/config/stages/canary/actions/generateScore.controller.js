'use strict';

angular.module('deckApp.pipelines.stage.canary.actions.generate.score.controller', [
  'ui.router',
  'deckApp.utils.lodash',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
])
  .controller('GenerateScoreCtrl', function ($scope, $http, $modalInstance, settings, canaryId) {

    $scope.command = {
      duration: null,
      durationUnit: 'h'
    };

    $scope.state = 'editing';

    this.generateCanaryScore = function() {
      $scope.state = 'submitting';
      var targetUrl = [settings.gateUrl, 'canaries', canaryId, 'generateCanaryResult'].join('/');
      var targetParams = 'duration=' + $scope.command.duration + '&durationUnit=' + $scope.command.durationUnit;
      $http.post([targetUrl, targetParams].join('?'), '')
        .success(function() {
          $scope.state = 'success';
        })
        .error(function() {
          $scope.state = 'failure';
        });
    };

    this.cancel = $modalInstance.dismiss;

  });
