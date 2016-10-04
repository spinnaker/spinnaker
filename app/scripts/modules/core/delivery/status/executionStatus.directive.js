'use strict';
import executionUserFilter from './executionUser.filter.ts';

let angular = require('angular');

require('./executionStatus.less');

module.exports = angular
  .module('spinnaker.core.delivery.executionStatus.directive', [
    require('../filter/executionFilter.model.js'),
    executionUserFilter
  ])
  .directive('executionStatus', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        execution: '=',
        toggleDetails: '=',
        showingDetails: '=',
        standalone: '=',
      },
      templateUrl: require('./executionStatus.html'),
      controller: 'executionStatus as vm',
    };
  })
  .controller('executionStatus', function(ExecutionFilterModel) {
    // these are internal parameters that are not useful to end users
    const strategyExclusions = [
      'parentPipelineId',
      'strategy',
      'parentStageId',
      'deploymentDetails',
      'cloudProvider'
    ];

    this.filter = ExecutionFilterModel.sortFilter;

    if (this.execution.trigger && this.execution.trigger.parameters) {
      this.parameters = Object.keys(this.execution.trigger.parameters).sort()
        .filter((paramKey) => this.execution.isStrategy ? strategyExclusions.indexOf(paramKey) < 0 : true)
        .map((paramKey) => {
          return { key: paramKey, value: this.execution.trigger.parameters[paramKey] };
        });
    }
  });
