'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { REST } from '@spinnaker/core';

export const CANARY_CANARY_ACTIONS_ENDCANARY_CONTROLLER = 'spinnaker.canary.actions.override.result.controller';
export const name = CANARY_CANARY_ACTIONS_ENDCANARY_CONTROLLER; // for backwards compatibility
module(CANARY_CANARY_ACTIONS_ENDCANARY_CONTROLLER, [UIROUTER_ANGULARJS]).controller('EndCanaryCtrl', [
  '$scope',
  '$uibModalInstance',
  'canaryId',
  function ($scope, $uibModalInstance, canaryId) {
    $scope.command = {
      reason: null,
      result: 'SUCCESS',
    };

    $scope.state = 'editing';

    this.endCanary = function () {
      $scope.state = 'submitting';
      REST('/canaries')
        .path(canaryId, 'end')
        .put($scope.command)
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
