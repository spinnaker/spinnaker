'use strict';

import {EXECUTION_SERVICE} from 'core/delivery/service/execution.service';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.triggers.pipeline.options.directive', [
    EXECUTION_SERVICE
  ])
  .directive('pipelineTriggerOptions', function () {
    return {
      restrict: 'E',
      templateUrl: require('./pipelineTriggerOptions.directive.html'),
      bindToController: {
        command: '=',
      },
      controller: 'PipelineTriggerOptionsCtrl',
      controllerAs: 'vm',
      scope: {}
    };
  })
  .controller('PipelineTriggerOptionsCtrl', function ($scope, executionService) {
    let executionLoadSuccess = (executions) => {
      this.executions = executions.filter((execution) => execution.pipelineConfigId === this.command.trigger.pipeline)
        .sort((a, b) => b.buildTime - a.buildTime);
      if (this.executions.length) {
        let defaultSelection = this.executions[0];
        this.viewState.selectedExecution = defaultSelection;
        this.updateSelectedExecution(defaultSelection);
      }
      this.viewState.executionsLoading = false;
    };

    let executionLoadFailure = () => {
      this.viewState.executionsLoading = false;
      this.viewState.loadError = true;
    };

    let initialize = () => {
      const command = this.command;

      // These fields will be added to the trigger when the form is submitted
      command.extraFields = {};

      this.viewState = {
        executionsLoading: true,
        loadError: false,
        selectedExecution: null,
      };

      // do not re-initialize if the trigger has changed to some other type
      if (command.trigger.type !== 'pipeline') {
        return;
      }
      this.viewState.executionsLoading = true;
      executionService.getExecutions(command.trigger.application, {statuses: []})
        .then(executionLoadSuccess, executionLoadFailure);
    };

    this.$onInit = () => initialize();

    this.updateSelectedExecution = (item) => {
      this.command.extraFields.parentPipelineId = item.id;
      this.command.extraFields.parentPipelineApplication = item.application;
    };

    $scope.$watch(() => this.command.trigger, initialize);

  });
