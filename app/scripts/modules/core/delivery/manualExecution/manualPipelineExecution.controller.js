'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.manualPipelineExecution.controller', [
  require('angular-ui-bootstrap'),
  require('../../utils/lodash.js'),
  require('../../pipeline/config/triggers/jenkins/jenkinsTrigger.module.js'),
  require('../../ci/jenkins/igor.service.js'),
])
  .controller('ManualPipelineExecutionCtrl', function (_, igorService, $modalInstance, pipeline, application) {

    this.command = {
      pipeline: pipeline,
      trigger: null,
      selectedBuild: null,
    };

    this.viewState = {
      buildsLoading: true,
    };

    let addTriggers = () => {
      if (!this.command.pipeline) {
        this.command.trigger = null;
        return;
      }

      this.triggers = _.chain(this.command.pipeline.triggers)
        .filter('type', 'jenkins')
        .sortBy('enabled')
        .reverse()
        .map((trigger) => {
          var copy = _.clone(trigger);
          copy.buildNumber = null;
          copy.type = 'manual';
          copy.description = copy.master + ': ' + copy.job;
          return copy;
        })
        .value();

      this.command.trigger  = _.first(this.triggers);
      this.builds = [];
    };


    /**
     * Controller API
     */

    this.triggerUpdated = (trigger) => {
      this.viewState.buildsLoading = true;
      let command = this.command;

      if( trigger !== undefined ) {
        command.trigger = trigger;
      }

      if (command.trigger) {
        this.viewState.buildsLoading = true;
        igorService.listBuildsForJob(command.trigger.master, command.trigger.job).then((builds) => {
          this.builds = _.filter(builds, {building: false, result: 'SUCCESS'});
          if (!angular.isDefined(command.trigger.build)) {
            command.selectedBuild = this.builds[0];
          }
          this.viewState.buildsLoading = false;
        });
      } else {
        this.builds = [];
        this.viewState.buildsLoading = false;
      }
    };

    this.pipelineSelected = () => {
      let pipeline = this.command.pipeline,
          executions = application.executions || [];
      this.currentlyRunningExecutions = executions
        .filter((execution) => execution.pipelineConfigId === pipeline.id && execution.isActive);
      addTriggers();
      this.triggerUpdated();

      this.showRebakeOption = pipeline.stages.some((stage) => stage.type === 'bake');

      if (pipeline.parameterConfig !== undefined && pipeline.parameterConfig.length) {
        this.parameters = {};
        pipeline.parameterConfig.forEach((parameter) => {
          this.parameters[parameter.name] = parameter.default;
        });
      }

    };

    this.updateSelectedBuild = (item) => {
      this.command.selectedBuild = item;
    };

    this.execute = () => {
      let selectedTrigger = this.command.trigger || {},
          command = { trigger: selectedTrigger },
          pipeline = this.command.pipeline;

      command.pipelineName = pipeline.name;

      if (selectedTrigger && this.command.selectedBuild) {
        selectedTrigger.buildNumber = this.command.selectedBuild.number;
      }
      if (pipeline.parameterConfig !== undefined && pipeline.parameterConfig.length) {
        selectedTrigger.parameters = this.parameters;
      }
      $modalInstance.close(command);
    };

    this.cancel = $modalInstance.dismiss;


    /**
     * Initialization
     */

    if (pipeline) {
      this.pipelineSelected();
    }

    if (!pipeline) {
      this.pipelineOptions = application.pipelineConfigs;
    }

  });
