'use strict';

let angular = require('angular');

require('./manualPipelineExecution.less');

module.exports = angular.module('spinnaker.core.delivery.manualPipelineExecution.controller', [
  require('angular-ui-bootstrap'),
  require('./inlinePropertyScope.filter'),
  require('../../utils/lodash.js'),
  require('../../pipeline/config/pipelineConfigProvider.js'),
  require('../../pipeline/config/services/pipelineConfigService')
])
  .controller('ManualPipelineExecutionCtrl', function (_, $uibModalInstance, pipeline, application, pipelineConfig, pipelineConfigService) {

    this.origPipeline = {};

    this.command = {
      pipeline: pipeline,
      trigger: null,
    };

    let addTriggers = () => {
      let pipeline = this.command.pipeline;
      if (!pipeline || !pipeline.triggers || !pipeline.triggers.length) {
        this.command.trigger = null;
        return;
      }

      this.triggers = pipeline.triggers
        .filter((trigger) => pipelineConfig.hasManualExecutionHandlerForTriggerType(trigger.type))
        .map((trigger) => {
          let copy = _.clone(trigger);
          copy.description = '...'; // placeholder
          pipelineConfig.getManualExecutionHandlerForTriggerType(trigger.type)
            .formatLabel(trigger).then((label) => copy.description = label);
          return copy;
        });

      this.command.trigger = _.first(this.triggers);
    };


    /**
     * Controller API
     */

    this.triggerUpdated = (trigger) => {
      let command = this.command;

      if( trigger !== undefined ) {
        command.trigger = trigger;
      }

      if (command.trigger) {
        this.triggerTemplate = pipelineConfig.getManualExecutionHandlerForTriggerType(command.trigger.type)
          .selectorTemplate;
      }
    };

    this.pipelineSelected = () => {
      let pipeline = this.command.pipeline,
          executions = application.executions.data || [];

      this.origPipeline = _.cloneDeep(pipeline); // make a copy to diff changes

      this.currentlyRunningExecutions = executions
        .filter((execution) => execution.pipelineConfigId === pipeline.id && execution.isActive);
      addTriggers();
      this.triggerUpdated();

      this.showRebakeOption = pipeline.stages.some((stage) => stage.type === 'bake');

      if (pipeline.parameterConfig && pipeline.parameterConfig.length) {
        this.parameters = {};
        pipeline.parameterConfig.forEach((parameter) => {
          this.parameters[parameter.name] = parameter.default;
        });
      }

    };

    this.pipelineIsDirty = () => {
      return !angular.equals(this.command.pipeline, this.origPipeline);
    };

    this.execute = () => {
      let selectedTrigger = this.command.trigger || {},
          command = { trigger: selectedTrigger },
          pipeline = this.command.pipeline;

      // include any extra data populated by trigger manual execution handlers
      angular.extend(selectedTrigger, this.command.extraFields);

      command.pipelineName = pipeline.name;
      selectedTrigger.type = 'manual';

      if (pipeline.parameterConfig && pipeline.parameterConfig.length) {
        selectedTrigger.parameters = this.parameters;
      }

      if (this.pipelineIsDirty()) {
        pipelineConfigService.savePipeline(pipeline)
          .then(() => $uibModalInstance.close(command) );
      } else {
        $uibModalInstance.close(command);
      }

    };

    this.cancel = $uibModalInstance.dismiss;

    this.hasStageOf = (stageType) => {
      return this.getStagesOf(stageType).length > 0;
    };

    this.getStagesOf = (stageType) => {
      return this.command.pipeline.stages.filter( stage => stage.type === stageType);
    };

    /**
     * Initialization
     */

    if (pipeline) {
      this.pipelineSelected();
    }

    if (!pipeline) {
      this.pipelineOptions = application.pipelineConfigs.data;
    }

  });
