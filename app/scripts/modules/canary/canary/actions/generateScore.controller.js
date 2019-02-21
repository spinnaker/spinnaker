'use strict';

const angular = require('angular');

import { SETTINGS } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.canary.actions.generate.score.controller', [require('@uirouter/angularjs').default])
  .controller('GenerateScoreCtrl', ['$scope', '$http', '$uibModalInstance', 'canaryId', function($scope, $http, $uibModalInstance, canaryId) {
    $scope.command = {
      duration: null,
      durationUnit: 'h',
    };

    $scope.state = 'editing';

    this.generateCanaryScore = function() {
      $scope.state = 'submitting';
      var targetUrl = [SETTINGS.gateUrl, 'canaries', canaryId, 'generateCanaryResult'].join('/');
      $http
        .post(targetUrl, $scope.command)
        .then(function onSuccess() {
          $scope.state = 'success';
        })
        .catch(function onError() {
          $scope.state = 'error';
        });
    };

    this.cancel = $uibModalInstance.dismiss;
  }]);
