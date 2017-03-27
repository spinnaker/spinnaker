'use strict';

let angular = require('angular');

import {SETTINGS} from 'core/config/settings';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.actions.generate.score.controller', [
  require('angular-ui-router'),
  require('core/delivery/details/executionDetailsSectionNav.directive.js'),
])
  .controller('GenerateScoreCtrl', function ($scope, $http, $uibModalInstance, canaryId) {

    $scope.command = {
      duration: null,
      durationUnit: 'h'
    };

    $scope.state = 'editing';

    this.generateCanaryScore = function() {
      $scope.state = 'submitting';
      var targetUrl = [SETTINGS.gateUrl, 'canaries', canaryId, 'generateCanaryResult'].join('/');
      $http.post(targetUrl, $scope.command)
        .success(function() {
          $scope.state = 'success';
        })
        .error(function() {
          $scope.state = 'error';
        });
    };

    this.cancel = $uibModalInstance.dismiss;

  });
