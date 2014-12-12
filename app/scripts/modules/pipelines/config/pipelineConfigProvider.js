'use strict';

angular.module('deckApp.pipelines')
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


    this.registerTrigger = registerTrigger;
    this.registerStage = registerStage;
    this.$get = function() {
      return {
        getTriggerTypes: getTriggerTypes,
        getStageTypes: getStageTypes
      };
    };

  }
);
