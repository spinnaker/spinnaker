'use strict';

import { EXECUTION_SERVICE } from 'core/pipeline/service/execution.service';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.triggers.pipeline.options.directive', [EXECUTION_SERVICE])
  .directive('pipelineTriggerOptions', function() {
    return {
      restrict: 'E',
      templateUrl: require('./pipelineTriggerOptions.directive.html'),
      bindToController: {
        command: '=',
      },
      controller: 'PipelineTriggerOptionsCtrl',
      controllerAs: 'vm',
      scope: {},
    };
  })
  .controller('PipelineTriggerOptionsCtrl', function($scope, executionService, executionsTransformer) {
    let executionLoadSuccess = executions => {
      this.executions = executions;
      if (this.executions.length) {
        this.executions.forEach(execution => executionsTransformer.addBuildInfo(execution));
        // default to what is supplied by the trigger if possible; otherwise, use the latest
        let defaultSelection =
          this.executions.find(e => e.id === this.command.trigger.parentPipelineId) || this.executions[0];
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
      // structure is a little different if this is a re-run; need to extract the fields from the parentExecution
      const parent = command.trigger.parentExecution;
      if (parent) {
        command.trigger.application = parent.application;
        command.trigger.pipeline = parent.pipelineConfigId;
        command.trigger.parentPipelineId = parent.id;
      }

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
      executionService
        .getExecutionsForConfigIds([this.command.trigger.pipeline], { limit: 20 })
        .then(executionLoadSuccess, executionLoadFailure);
    };

    this.$onInit = () => initialize();

    this.updateSelectedExecution = item => {
      this.command.extraFields.parentPipelineId = item.id;
      this.command.extraFields.parentPipelineApplication = item.application;
    };

    $scope.$watch(() => this.command.trigger, initialize);
  });
