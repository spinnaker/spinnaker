'use strict';

angular.module('deckApp.pipelines.stage.canary.actions.override.result.controller', [
  'ui.router',
  'deckApp.utils.lodash',
  'deckApp.executionDetails.section.service',
  'deckApp.executionDetails.section.nav.directive',
])
  .controller('EndCanaryCtrl', function ($scope, $http, $modalInstance, settings, canaryId) {

    $scope.command = {
      reason: null,
      result: 'SUCCESS',
    };

    $scope.state = 'editing';

    this.endCanary = function() {
      $scope.state = 'submitting';
      var targetUrl = [settings.gateUrl, 'canaries', canaryId, 'end'].join('/');
      $http.delete(targetUrl, $scope.command)
        .success(function() {
          $scope.state = 'success';
        })
        .error(function() {
          $scope.state = 'error';
        });
    };

    this.cancel = $modalInstance.dismiss;

  });
