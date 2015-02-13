'use strict';

angular.module('deckApp.pipelines.stage.executionWindows.controller', [
  'deckApp.utils.timePicker.service'
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
      }
    };

    this.removeWindow = function(index) {
      $scope.stage.restrictedExecutionWindow.whitelist.splice(index, 1);
    };

    $scope.$watch('stage.restrictExecutionDuringTimeWindow', this.toggleWindowRestriction);

    this.getWindowStyle = function(window) {
      var dayMinutes = 24*60;
      var start = window.startHour * 60 + window.startMin,
        end = window.endHour * 60 + window.endMin,
        width = (end - start)/dayMinutes*100,
        startOffset = start/dayMinutes*100;

      return {
        width: width + '%',
        left: startOffset + '%',
      };
    };

    $scope.dividers = [];
    $scope.hours.forEach(function(hour) {
      $scope.dividers.push({
        label: hour.label,
        left: (hour.key/24 * 100) + '%',
      });
    });

  });
