'use strict';

let angular = require('angular');

require('./projectPipeline.less');

module.exports = angular.module('spinnaker.core.projects.dashboard.pipelines.projectPipeline.directive', [
  require('../../../utils/lodash.js'),
])
  .directive('projectPipeline', function () {
    return {
      restrict: 'E',
      templateUrl: require('./projectPipeline.directive.html'),
      scope: {},
      bindToController: {
        execution: '=',
      },
      controller: 'ProjectPipelineCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ProjectPipelineCtrl', function(_) {
    this.state = {
      loaded: false,
    };
    this.state.loaded = true;
    this.stageWidth = 100 / this.execution.stageSummaries.length + '%';

    this.hasBuildInfo = this.execution.buildInfo || _.has(this.execution, 'trigger.buildInfo') || _.has(this.execution, 'trigger.parentPipelineId');

  });
