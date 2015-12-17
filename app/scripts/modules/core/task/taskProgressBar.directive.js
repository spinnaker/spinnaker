'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.task.progressBar.directive', [])
  .directive('taskProgressBar', function($filter, $sce) {
    return {
      restrict: 'E',
      scope: {
        task: '='
      },
      templateUrl: require('./taskProgressBar.directive.html'),
      link: function(scope) {
        var task = scope.task;

        var stepsComplete = task.steps.filter(function(step) {
          return step.isCompleted;
        });

        scope.progressStyle = { width: stepsComplete.length / task.steps.length * 100 + '%' };

        if (task.isRunning) {
          var currentStep = task.steps.filter(function(step) {
            return step.hasNotStarted || step.isRunning;
          });

          var currentStepIndex = task.steps.indexOf(currentStep[0]) + 1;

          scope.tooltip = $sce.trustAsHtml('Step ' + currentStepIndex + ' of ' + task.steps.length + ': ' + $filter('robotToHuman')(currentStep[0].name));
        }

        if (task.isFailed) {
          var failedStep = task.steps.filter(function(step) {
            return step.isFailed || step.isSuspended;
          });

          if (failedStep.length) {
            var failedStepIndex = task.steps.indexOf(failedStep[0]) + 1;
            var clipped = task.failureMessage.length > 400 ? task.failureMessage.substring(0, 400) + '&hellip;' : task.failureMessage;
            scope.tooltip = $sce.trustAsHtml('Failed on Step ' + failedStepIndex + ' of ' + task.steps.length + ':<br>' + $filter('robotToHuman')(failedStep[0].name) +
              '<br><br><strong>Exception:</strong><p>' + clipped + '</p>');
          } else {
            scope.tooltip = $sce.trustAsHtml('Task failed; sorry, no reason provided');
          }
        }
      }
    };
  });
