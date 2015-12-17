'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.actions.generate.score.controller', [
  require('angular-ui-router'),
  require('../../../../../core/utils/lodash.js'),
  require('../../../../../core/delivery/details/executionDetailsSection.service.js'),
  require('../../../../../core/delivery/details/executionDetailsSectionNav.directive.js'),
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
