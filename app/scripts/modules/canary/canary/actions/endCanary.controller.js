'use strict';

const angular = require('angular');

import { SETTINGS } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.canary.actions.override.result.controller', [require('@uirouter/angularjs').default])
  .controller('EndCanaryCtrl', ['$scope', '$http', '$uibModalInstance', 'canaryId', function($scope, $http, $uibModalInstance, canaryId) {
    $scope.command = {
      reason: null,
      result: 'SUCCESS',
    };

    $scope.state = 'editing';

    this.endCanary = function() {
      $scope.state = 'submitting';
      var targetUrl = [SETTINGS.gateUrl, 'canaries', canaryId, 'end'].join('/');
      $http
        .put(targetUrl, $scope.command)
        .then(function onSuccess() {
          $scope.state = 'success';
        })
        .catch(function onError() {
          $scope.state = 'error';
        });
    };

    this.cancel = $uibModalInstance.dismiss;
  }]);
