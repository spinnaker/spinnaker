'use strict';

const angular = require('angular');
import { Subject } from 'rxjs';

import { EXECUTION_WINDOW_ATLAS_GRAPH } from './atlasGraph.component';
import { EXECUTION_WINDOWS_DAY_PICKER } from './executionWindowDayPicker.component';
import { DEFAULT_SKIP_WINDOW_TEXT } from './ExecutionWindowActions';
import { TimePickerOptions } from 'core/utils/TimePickerOptions';

module.exports = angular
  .module('spinnaker.core.pipeline.stage.executionWindows.controller', [
    EXECUTION_WINDOWS_DAY_PICKER,
    EXECUTION_WINDOW_ATLAS_GRAPH,
  ])
  .controller('ExecutionWindowsCtrl', function($scope) {
    this.windowsUpdatedStream = new Subject();
    this.enableCustomSkipWindowText = false;

    $scope.hours = TimePickerOptions.getHours();
    $scope.minutes = TimePickerOptions.getMinutes();

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

    this.updateTimelineWindows = () => {
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
              endHour: 24,
              wrapEnd: true,
            },
            secondWindow = {
              startMin: 0,
              startHour: 0,
              endMin: window.endMin,
              endHour: window.endHour,
            };
          windows.push(buildTimelineWindow(firstWindow, window));
          windows.push(buildTimelineWindow(secondWindow, window));
        } else {
          windows.push(buildTimelineWindow(window));
        }
      });
      $scope.timelineWindows = windows;
      this.windowsUpdated();
    };

    this.windowsUpdated = () => {
      this.windowsUpdatedStream.next($scope.timelineWindows);
    };

    function buildTimelineWindow(window, originalWindow) {
      var labelRef = originalWindow || window;
      const timelineWindow = {
        style: getWindowStyle(window),
        start: new Date(2000, 1, 1, labelRef.startHour, labelRef.startMin),
        end: new Date(2000, 1, 1, labelRef.endHour, labelRef.endMin),
        displayStart: new Date(2000, 1, 1, window.startHour, window.startMin),
        displayEnd: new Date(2000, 1, 1, window.endHour, window.endMin),
      };
      if (window.wrapEnd) {
        timelineWindow.displayEnd = new Date(2000, 1, 1, 23, 59, 59, 999);
      }
      return timelineWindow;
    }

    function getWindowStyle(window) {
      var dayMinutes = 24 * 60;
      var start = window.startHour * 60 + window.startMin,
        end = window.endHour * 60 + window.endMin,
        width = ((end - start) / dayMinutes) * 100,
        startOffset = (start / dayMinutes) * 100;

      return {
        width: width + '%',
        left: startOffset + '%',
      };
    }

    this.toggleWindowJitter = function(newVal) {
      if (newVal) {
        if (!$scope.stage.restrictedExecutionWindow.jitter.minDelay) {
          $scope.stage.restrictedExecutionWindow.jitter.minDelay = 0;
        }
        if (!$scope.stage.restrictedExecutionWindow.jitter.maxDelay) {
          $scope.stage.restrictedExecutionWindow.jitter.maxDelay = 600;
        }
        if ($scope.stage.restrictedExecutionWindow.jitter.skipManual == null) {
          $scope.stage.restrictedExecutionWindow.jitter.skipManual = true;
        }
      } else {
        if ($scope.stage.restrictedExecutionWindow) {
          delete $scope.stage.restrictedExecutionWindow.jitter;
        }
      }
    };

    this.jitterUpdated = () => {
      if (!$scope.stage.restrictedExecutionWindow.jitter) {
        return;
      }
      var jitter = $scope.stage.restrictedExecutionWindow.jitter;
      if (jitter.minDelay >= 0 && jitter.maxDelay <= jitter.minDelay) {
        jitter.maxDelay = jitter.minDelay + 1;
      }
    };

    $scope.dividers = [];
    $scope.hours.forEach(function(hour) {
      $scope.dividers.push({
        label: hour.label,
        left: (hour.value / 24) * 100 + '%',
      });
    });

    this.enableCustomSkipWindowText = !!$scope.stage.skipWindowText;
    this.defaultSkipWindowText = DEFAULT_SKIP_WINDOW_TEXT;

    $scope.$watch('stage.restrictedExecutionWindow.whitelist', this.updateTimelineWindows, true);
    $scope.$watch('stage.restrictExecutionDuringTimeWindow', this.toggleWindowRestriction);
    $scope.$watch('stage.restrictedExecutionWindow.jitter.enabled', this.toggleWindowJitter);
  });
