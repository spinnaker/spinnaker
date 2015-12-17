'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.instance.details.scheduledAction.directive', [
  require('../../../../core/insight/insightFilterState.model.js'),
])
  .directive('azureScheduledAction', function(InsightFilterStateModel) {
    return {
      restrict: 'E',
      scope: {
        action: '='
      },
      templateUrl: require('./scheduledAction.directive.html'),
      link: function(scope) {
        scope.InsightFilterStateModel = InsightFilterStateModel;
      }
    };
  });
