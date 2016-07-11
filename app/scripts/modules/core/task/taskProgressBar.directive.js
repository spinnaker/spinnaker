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
          let [currentStep] = task.steps.filter(step => step.hasNotStarted || step.isRunning);
          var currentStepIndex = task.steps.indexOf(currentStep[0]) + 1;

          if (currentStep) {
            scope.tooltip = $sce.trustAsHtml('Step ' + currentStepIndex + ' of ' + task.steps.length + ': ' + $filter('robotToHuman')(currentStep.name));
          }
        }

        if (task.isFailed) {
          var [failedStep] = task.steps.filter(step => step.isFailed || step.isSuspended);

          if (failedStep && task.failureMessage) {
            var failedStepIndex = task.steps.indexOf(failedStep) + 1;
            var clipped = task.failureMessage.length > 400 ? task.failureMessage.substring(0, 400) + '&hellip;' : task.failureMessage;
            scope.tooltip = $sce.trustAsHtml('Failed on Step ' + failedStepIndex + ' of ' + task.steps.length + ':<br>' + $filter('robotToHuman')(failedStep.name) +
              '<br><br><strong>Exception:</strong><p>' + clipped + '</p>');
          } else {
            scope.tooltip = $sce.trustAsHtml('Task failed; sorry, no reason provided');
          }
        }
      }
    };
  });
