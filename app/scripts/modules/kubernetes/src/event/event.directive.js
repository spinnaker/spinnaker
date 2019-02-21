'use strict';

const angular = require('angular');

import { ServerGroupTemplates } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.kubernetes.event.event.directive', [])
  .directive('kubernetesEvent', function() {
    return {
      restrict: 'E',
      templateUrl: require('./event.directive.html'),
      scope: {
        event: '=',
      },
    };
  })
  .controller('kubernetesEventController', [
    '$scope',
    '$uibModal',
    function($scope, $uibModal) {
      if ($scope.event.message) {
        this.displayMessage = $scope.event.message.substring(0, 40);
      }

      this.type = $scope.event.type;
      this.count = $scope.event.count;
      this.first = $scope.event.firstOccurrence;
      this.last = $scope.event.lastOccurrence;
      this.reason = $scope.event.reason;

      this.showMessage = function showMessage() {
        $scope.userDataModalTitle = 'Message';
        $scope.userData = $scope.event.message;
        $uibModal.open({
          templateUrl: ServerGroupTemplates.userData,
          scope: $scope,
        });
      };
    },
  ]);
