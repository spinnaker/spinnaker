'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.fastProperties.progressBar.directive', [
  require('../../core/utils/lodash.js'),
  require('./fastPropertyScope.service.js')
])
  .directive('fastPropertyProgressBar', function(_, FastPropertyScopeService) {
    return {
      restrict: 'E',
      scope: {
        task: '='
      },
      templateUrl: require('./fastPropertyProgressBar.directive.html'),
      link: function(scope) {
        var task = scope.task;


        let extractScopeFromHistoryMessage = (messageString) => {
          let regex = /(?:Scope\()(.+?)\)/;
          let prefexRegex = /.+?(?=Selection)/;
          let prefixResult = prefexRegex.exec(messageString);
          let resultArray = regex.exec(messageString) || [];
          return prefixResult && resultArray.length > 1 ? `${prefixResult}: ${resultArray[1].split(',').join(', ')}` : messageString;
        };

        scope.isRunning = task.state === 'Running';
        scope.isFailed = task.state === 'Failed';
        scope.isSuccessful = task.state === 'Successful';
        scope.isPending = task.state === 'Pending';

        var currentStep = task.range ? task.range.currentStep : 0;

        var totalSteps = task.range ? task.range.totalSteps : 0;

        scope.progressStyle = { width: currentStep / totalSteps * 100 + '%' };

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
            '<br><br><strong>Exception:</strong><p>' + extractScopeFromHistoryMessage( lastHistory.message )+'</p>';
        }
      },
      controller: function() {
        const vm = this;

        vm.extractScopeFromHistoryMessage = FastPropertyScopeService.extractScopeFromHistoryMessage;

        return vm;
      },
      controllerAs:'ctrl'
    };
  });
