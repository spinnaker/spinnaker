'use strict';

angular.module('deckApp.pipelines.config', [])
  .provider('pipelineConfig', function() {

    var triggerTypes = [],
        stageTypes = [];

    function registerTrigger(triggerConfig) {
      triggerTypes.push(triggerConfig);
    }

    function registerStage(stageConfig) {
      stageTypes.push(stageConfig);
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

    this.registerTrigger = registerTrigger;
    this.registerStage = registerStage;
    this.$get = function() {
      return {
        getTriggerTypes: getTriggerTypes,
        getStageTypes: getStageTypes,
        getStageConfig: getStageConfig,
        getConfigurableStageTypes: getConfigurableStageTypes,
      };
    };

  }
);
