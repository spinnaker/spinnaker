'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.fastProperty.progressBar.directive', [
  require('utils/lodash.js'),
])
  .directive('fastPropertyProgressBar', function(_) {
    return {
      restrict: 'E',
      scope: {
        task: '='
      },
      templateUrl: require('./fastPropertyProgressBar.directive.html'),
      link: function(scope) {
        var task = scope.task;

        scope.isRunning = task.state === 'Running';
        scope.isFailed = task.state === 'Failed';
        scope.isSuccessful = task.state === 'Successful';
        scope.isPending = task.state === 'Pending';


        var currentStep = task.scopes.stepNo;
        var totalSteps = task.scopes.steps;

        scope.progressStyle = { width: currentStep / task.scopes.steps * 100 + '%' };

        scope.tooltip = currentStep;

        if (scope.isRunning) {
          scope.tooltip = 'Step ' + currentStep + ' of ' + totalSteps;
        }

        if (scope.isSuccessful) {
          scope.tooltip = 'Step ' + totalSteps + ' of ' + totalSteps;
          scope.progressStyle = { width: '100%' };
        }

        if (scope.isFailed) {
          var lastHistory = _(task.history).last();
          scope.progressStyle = { width: '100%' };
          // XSS_TODO
          scope.tooltip = 'Failed on Step ' + currentStep + ' of ' + totalSteps +
            '<br><br><strong>Exception:</strong><p>' + lastHistory.message +'</p>';
        }
      }
    };
  });
