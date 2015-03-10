'use strict';

angular.module('deckApp.pipelines.config.validator.service', [
  'deckApp.pipelines.config',
  'deckApp.utils.lodash',
])
  .factory('pipelineConfigValidator', function(pipelineConfig, $log, _) {

    var validators = {
      stageBeforeType: function(pipeline, index, validationConfig, messages) {
        var stageTypes = validationConfig.stageTypes || [validationConfig.stageType];
        for (var i = 0; i < index; i++) {
          if (stageTypes.indexOf(pipeline.stages[i].type) !== -1) {
            return;
          }
        }
        messages.push(validationConfig.message);
      },
      stageBeforeMethod: function(pipeline, index, validationConfig, messages) {
        if (index === 0) {
          return;
        }
        var thisStage = pipeline.stages[index],
            beforeStage = pipeline.stages[index-1];

        var validationMessage = validationConfig.validate(beforeStage, thisStage);
        if (validationMessage) {
          messages.push(validationMessage);
        }
      },
      checkRequiredField: function(stage, validationConfig, messages) {
        var field = stage,
            parts = validationConfig.fieldName.split('.'),
            fieldNotFound = false;

        parts.forEach(function(part) {
          if (field[part] === undefined) {
            fieldNotFound = true;
            return;
          }
          field = field[part];
        });

        if (fieldNotFound || (!field && field !== 0)) {
          messages.push(validationConfig.message);
        }
      },
    };

    function validatePipeline(pipeline) {
      var stages = pipeline.stages || [],
          messages = [];
      stages.forEach(function(stage, index) {
        var config = pipelineConfig.getStageConfig(stage.type);
        if (config && config.validators) {
          config.validators.forEach(function(validator) {
            switch(validator.type) {
              case 'stageBeforeType':
                validators.stageBeforeType(pipeline, index, validator, messages);
                break;
              case 'stageBeforeMethod':
                validators.stageBeforeMethod(pipeline, index, validator, messages);
                break;
              case 'requiredField':
                validators.checkRequiredField(stage, validator, messages);
                break;
              default:
                $log.warn('No validator of type "' + validator.type + '" found, ignoring validation on stage "' + stage.name + '" (' + stage.type + ')');
            }
          });
        }
      });
      return _.uniq(messages);
    }

    return {
      validatePipeline: validatePipeline,
    };
  });
