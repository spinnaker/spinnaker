'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.executionWindows.controller', [
  require('../../../../utils/timePicker.service.js')
])
  .controller('ExecutionWindowsCtrl', function($scope, timePickerService) {

    $scope.hours = timePickerService.getHours();
    $scope.minutes = timePickerService.getMinutes();

    this.addExecutionWindow = function() {
      $scope.stage.restrictedExecutionWindow.whitelist.push({
        startHour: 0,
        startMin: 0,
        endHour: 0,
        endMin: 0,
      });
    };

    this.toggleWindowRestriction = function(newVal) {
      if (newVal) {
        if (!$scope.stage.restrictedExecutionWindow) {
          $scope.stage.restrictedExecutionWindow = {
            whitelist: [],
          };
        }
      } else {
        delete $scope.stage.restrictedExecutionWindow;
      }
    };

    this.removeWindow = function(index) {
      $scope.stage.restrictedExecutionWindow.whitelist.splice(index, 1);
    };

    this.updateTimelineWindows = function() {
      if (!$scope.stage.restrictedExecutionWindow) {
        return;
      }
      var windows = [];
      $scope.stage.restrictedExecutionWindow.whitelist.forEach(function(window) {
        var start = window.startHour * 60 + window.startMin,
          end = window.endHour * 60 + window.endMin;

        // split into two windows
        if (start > end) {
          var firstWindow = {
              startMin: window.startMin,
              startHour: window.startHour,
              endMin: 0,
              endHour: 24
            },
            secondWindow = {
              startMin: 0,
              startHour: 0,
              endMin: window.endMin,
              endHour: window.endHour
            };
          windows.push(buildTimelineWindow(firstWindow, window));
          windows.push(buildTimelineWindow(secondWindow, window));
        } else {
          windows.push(buildTimelineWindow(window));
        }
      });
      $scope.timelineWindows = windows;
    };

    function buildTimelineWindow(window, originalWindow) {
      var labelRef = originalWindow || window;
      return {
        style: getWindowStyle(window),
        start: new Date(2000, 1, 1, labelRef.startHour, labelRef.startMin),
        end: new Date(2000, 1, 1, labelRef.endHour, labelRef.endMin),
      };
    }

    function getWindowStyle(window) {
      var dayMinutes = 24 * 60;
      var start = window.startHour * 60 + window.startMin,
        end = window.endHour * 60 + window.endMin,
        width = (end - start) / dayMinutes * 100,
        startOffset = start / dayMinutes * 100;

      return {
        width: width + '%',
        left: startOffset + '%',
      };
    }

    $scope.dividers = [];
    $scope.hours.forEach(function(hour) {
      $scope.dividers.push({
        label: hour.label,
        left: (hour.key / 24 * 100) + '%',
      });
    });

    $scope.$watch('stage.restrictedExecutionWindow.whitelist', this.updateTimelineWindows, true);
    $scope.$watch('stage.restrictExecutionDuringTimeWindow', this.toggleWindowRestriction);

  });
