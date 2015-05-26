'use strict';

angular.module('spinnaker.pipelines.stage.canary.actions.generate.score.controller', [
  'ui.router',
  'spinnaker.utils.lodash',
  'spinnaker.executionDetails.section.service',
  'spinnaker.executionDetails.section.nav.directive',
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
      $http.post(targetUrl, $scope.command)
        .success(function() {
          $scope.state = 'success';
        })
        .error(function() {
          $scope.state = 'error';
        });
    };

    this.cancel = $modalInstance.dismiss;

  });
