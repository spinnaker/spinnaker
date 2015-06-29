'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config.configProvider', [
])
  .provider('pipelineConfig', function() {

    var triggerTypes = [],
        stageTypes = [],
        transformers = [];

    function registerTrigger(triggerConfig) {
      triggerTypes.push(triggerConfig);
    }

    function registerTransformer(transformer) {
      transformers.push(transformer);
    }

    function registerStage(stageConfig) {
      stageTypes.push(stageConfig);
    }

    function getExecutionTransformers() {
      return transformers;
    }

    function getTriggerTypes() {
      return angular.copy(triggerTypes);
    }

    function getStageTypes() {
      return angular.copy(stageTypes);
    }

    function getConfigurableStageTypes() {
      return getStageTypes().filter(function(stageType) {
        return !stageType.synthetic;
      });
    }

    function getStageConfig(type) {
      var matches = getStageTypes().filter(function(stageType) { return stageType.key === type; });
      return matches.length ? matches[0] : null;
    }

    function getTriggerConfig(type) {
      var matches = getTriggerTypes().filter(function(triggerType) { return triggerType.key === type; });
      return matches.length ? matches[0] : null;
    }

    this.registerTrigger = registerTrigger;
    this.registerStage = registerStage;
    this.$get = function() {
      return {
        getTriggerTypes: getTriggerTypes,
        getStageTypes: getStageTypes,
        getTriggerConfig: getTriggerConfig,
        getStageConfig: getStageConfig,
        getConfigurableStageTypes: getConfigurableStageTypes,
        getExecutionTransformers: getExecutionTransformers,
        registerTransformer: registerTransformer,
      };
    };

  }
);
