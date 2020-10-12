'use strict';

import { module } from 'angular';

import { SETTINGS } from '@spinnaker/core';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';

export const CANARY_CANARY_ACTIONS_ENDCANARY_CONTROLLER = 'spinnaker.canary.actions.override.result.controller';
export const name = CANARY_CANARY_ACTIONS_ENDCANARY_CONTROLLER; // for backwards compatibility
module(CANARY_CANARY_ACTIONS_ENDCANARY_CONTROLLER, [UIROUTER_ANGULARJS]).controller('EndCanaryCtrl', [
  '$scope',
  '$http',
  '$uibModalInstance',
  'canaryId',
  function ($scope, $http, $uibModalInstance, canaryId) {
    $scope.command = {
      reason: null,
      result: 'SUCCESS',
    };

    $scope.state = 'editing';

    this.endCanary = function () {
      $scope.state = 'submitting';
      const targetUrl = [SETTINGS.gateUrl, 'canaries', canaryId, 'end'].join('/');
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
  },
]);
