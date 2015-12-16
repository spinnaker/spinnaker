'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.overrideTimeout', [
  require('../../pipelineConfigProvider.js'),
  require('../../../../help/helpContents.js')
])
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
  .controller('OverrideTimeoutCtrl', function($scope, pipelineConfig, helpContents) {
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
      var stage = $scope.stage,
          stageConfig = pipelineConfig.getStageConfig(stage),
          stageDefaults = stageConfig ? stageConfig.defaultTimeoutMs : null;

      $scope.vm = {
        configurable: !!stageDefaults
      };

      $scope.vm.helpContent = helpContents['pipeline.config.timeout'] + helpContents['pipeline.config.timeout.' + stage.type];
      $scope.vm.defaults = toHoursAndMinutes(stageDefaults);

      if (stage.overrideTimeout) {
        var overrideValue = stage.stageTimeoutMs || stageDefaults;
        $scope.vm.hours = toHoursAndMinutes(overrideValue).hours;
        $scope.vm.minutes = toHoursAndMinutes(overrideValue).minutes;
      } else {
        delete stage.stageTimeoutMs;
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

  });
