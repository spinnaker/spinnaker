'use strict';

const angular = require('angular');

import { Registry } from 'core/registry';
import { HelpContentsRegistry } from 'core/help';

module.exports = angular
  .module('spinnaker.core.pipeline.stage.overrideTimeout', [])
  .directive('overrideTimeout', function() {
    return {
      restrict: 'E',
      scope: {
        stage: '=',
      },
      templateUrl: require('./overrideTimeout.directive.html'),
      controller: 'OverrideTimeoutCtrl',
      controllerAs: 'overrideTimeoutCtrl',
    };
  })
  .controller('OverrideTimeoutCtrl', [
    '$scope',
    function($scope) {
      function toHoursAndMinutes(ms) {
        if (!ms) {
          return { hours: 0, minutes: 0 };
        } else {
          var seconds = ms / 1000;
          return {
            hours: Math.floor(seconds / 3600),
            minutes: Math.floor(seconds / 60) % 60,
          };
        }
      }

      this.setOverrideValues = function() {
        const stage = $scope.stage,
          stageConfig = Registry.pipeline.getStageConfig(stage),
          stageDefaults = stageConfig ? stageConfig.defaultTimeoutMs : null;

        // Capturing this so that we can restore it after $scope.vm is clobbered
        const originalOverrideTimeout = $scope.vm && $scope.vm.overrideTimeout;
        // vm.overrideTimeout being false is only a transition state to indicate the override got unchecked
        const shouldRemoveOverride = originalOverrideTimeout === false;

        $scope.vm = {
          configurable: !!stageDefaults,
        };

        $scope.vm.helpContent = HelpContentsRegistry.getHelpField('pipeline.config.timeout');
        $scope.vm.defaults = toHoursAndMinutes(stageDefaults);

        if (shouldRemoveOverride) {
          delete stage.stageTimeoutMs;
        } else if (originalOverrideTimeout || stage.stageTimeoutMs !== undefined) {
          // Either vm.overrideTimeout was originally true, or forcing to true because stageTimeoutMs is defined
          $scope.vm.overrideTimeout = true;

          stage.stageTimeoutMs = stage.stageTimeoutMs || stageDefaults;
          $scope.vm.hours = toHoursAndMinutes(stage.stageTimeoutMs).hours;
          $scope.vm.minutes = toHoursAndMinutes(stage.stageTimeoutMs).minutes;
        }
      };

      this.synchronizeTimeout = function() {
        var timeout = 0,
          vm = $scope.vm;
        if (!isNaN(vm.minutes)) {
          timeout += 60 * 1000 * parseInt(vm.minutes);
        }
        if (!isNaN(vm.hours)) {
          timeout += 60 * 60 * 1000 * parseInt(vm.hours);
        }
        $scope.stage.stageTimeoutMs = timeout;
      };

      $scope.$watch('stage', this.setOverrideValues, true);
    },
  ]);
