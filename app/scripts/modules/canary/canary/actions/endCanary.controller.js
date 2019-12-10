'use strict';

const angular = require('angular');

import { SETTINGS } from '@spinnaker/core';

export const CANARY_CANARY_ACTIONS_ENDCANARY_CONTROLLER = 'spinnaker.canary.actions.override.result.controller';
export const name = CANARY_CANARY_ACTIONS_ENDCANARY_CONTROLLER; // for backwards compatibility
angular
  .module(CANARY_CANARY_ACTIONS_ENDCANARY_CONTROLLER, [require('@uirouter/angularjs').default])
  .controller('EndCanaryCtrl', [
    '$scope',
    '$http',
    '$uibModalInstance',
    'canaryId',
    function($scope, $http, $uibModalInstance, canaryId) {
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
    },
  ]);
