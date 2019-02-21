'use strict';

import _ from 'lodash';

const angular = require('angular');

require('./editNotification.html');

import './editNotification.less';

module.exports = angular
  .module('spinnaker.core.notification.modal.editNotification.modal.controller', [])
  .controller('EditNotificationController', [
    '$scope',
    '$uibModalInstance',
    'notification',
    'level',
    'stageType',
    function($scope, $uibModalInstance, notification, level, stageType) {
      var vm = this;

      vm.notification = angular.copy(notification);
      vm.hasSelectedWhen = false;
      $scope.selectedWhenOptions = {};
      $scope.level = level;
      $scope.stageType = stageType;

      if (level === 'application' || level === 'pipeline') {
        vm.whenOptions = ['pipeline.starting', 'pipeline.complete', 'pipeline.failed'];
      } else if (stageType === 'manualJudgment') {
        vm.whenOptions = ['manualJudgment', 'manualJudgmentContinue', 'manualJudgmentStop'];
      } else {
        vm.whenOptions = ['stage.starting', 'stage.complete', 'stage.failed'];
      }

      vm.updateSelectedWhen = function() {
        var selected = false;
        _.each(vm.whenOptions, function(option) {
          if ($scope.selectedWhenOptions[option] === true) {
            selected = true;
          }
        });
        vm.hasSelectedWhen = selected;
      };

      if (vm.notification !== undefined) {
        _.each(vm.whenOptions, function(option) {
          if (vm.notification.when.includes(option)) {
            $scope.selectedWhenOptions[option] = true;
          }
        });
        vm.updateSelectedWhen();
      } else {
        vm.notification = {};
        vm.notification.level = $scope.level;
        vm.notification.when = [];
      }

      vm.submit = function() {
        vm.notification.when = [];
        _.each(vm.whenOptions, function(option) {
          if ($scope.selectedWhenOptions[option] === true) {
            vm.notification.when.push(option);
          }
        });
        $uibModalInstance.close(vm.notification);
      };

      vm.supportsCustomMessage = function(notification, whenOption) {
        return whenOption !== 'manualJudgment' && ['email', 'slack', 'googlechat'].includes(notification.type);
      };

      vm.stageType = function() {
        return $scope.stageType;
      };

      $scope.$watch(
        'selectedWhenOptions',
        function(a, b) {
          if (a !== b) {
            vm.updateSelectedWhen();
          }
        },
        true,
      );

      return vm;
    },
  ]);
