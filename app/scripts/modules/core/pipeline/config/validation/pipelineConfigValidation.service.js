'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.validator.service', [
  require('../pipelineConfigProvider.js'),
  require('../services/pipelineConfigService.js'),
  require('../../../naming/naming.service.js'),
  require('../../../utils/lodash.js'),
])
  .factory('pipelineConfigValidator', function($log, _, pipelineConfig, pipelineConfigService, namingService) {

    var validators = {
      stageBeforeType: function(pipeline, index, validationConfig, messages) {
        var stageTypes = validationConfig.stageTypes || [validationConfig.stageType];
        var stagesToTest = pipeline.stages.slice(0, index+1);
        if (pipeline.parallel) {
          stagesToTest = pipelineConfigService.getAllUpstreamDependencies(pipeline, pipeline.stages[index]);
        }
        for (var i = 0; i < stagesToTest.length; i++) {
          if (stageTypes.indexOf(stagesToTest[i].type) !== -1) {
            return;
          }
        }
        messages.push(validationConfig.message);
      },
      checkRequiredField: function(stage, validationConfig, config, messages) {
        let fieldLabel = validationConfig.fieldLabel || validationConfig.fieldName;
        fieldLabel = fieldLabel.charAt(0).toUpperCase() + fieldLabel.substr(1);
        let validationMessage = validationConfig.message ||
          '<strong>' + fieldLabel + '</strong> is a required field for ' + config.label + ' stages.';
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


        if (fieldNotFound ||
          (!field && field !== 0) ||
          (field && field instanceof Array && field.length === 0)
          ) {
          messages.push(validationMessage);
        }
      },
      targetImpedance: function(pipeline, index, validationConfig, messages) {
        var stage = pipeline.stages[index],
            stagesToTest = pipeline.stages.slice(0, index+1),
            regions = stage.regions || [],
            allRegionsFound = true;

        if (pipeline.parallel) {
          stagesToTest = pipelineConfigService.getAllUpstreamDependencies(pipeline, pipeline.stages[index]);
        }

        regions.forEach(function(region) {
          var regionFound = false;
          stagesToTest.forEach(function(toTest) {
            if (toTest.type === 'deploy' && toTest.clusters && toTest.clusters.length) {
              toTest.clusters.forEach(function(cluster) {
                var clusterName = namingService.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
                if (clusterName === stage.cluster && cluster.account === stage.credentials && cluster.availabilityZones.hasOwnProperty(region)) {
                  regionFound = true;
                }
              });
            }
          });
          if (!regionFound) {
            allRegionsFound = false;
          }
        });
        if (!allRegionsFound) {
          messages.push(validationConfig.message);
        }
      }
    };

    function validatePipeline(pipeline) {
      var stages = pipeline.stages || [],
          triggers = pipeline.triggers || [],
          messages = [];

      triggers.forEach(function(trigger, index) {
        let config = pipelineConfig.getTriggerConfig(trigger.type);
        if (config && config.validators) {
          config.validators.forEach(function(validator) {
            switch(validator.type) {
              case 'requiredField':
                validators.checkRequiredField(trigger, validator, config, messages);
                break;
              default:
                $log.warn('No validator of type "' + validator.type + '" found, ignoring validation on trigger "' + (index+1) + '" (' + trigger.type + ')');
            }
          });
        }
      });
      stages.forEach(function(stage, index) {
        let config = pipelineConfig.getStageConfig(stage);
        if (config && config.validators) {
          config.validators.forEach(function(validator) {
            switch(validator.type) {
              case 'stageBeforeType':
                validators.stageBeforeType(pipeline, index, validator, messages);
                break;
              case 'targetImpedance':
                validators.targetImpedance(pipeline, index, validator, messages);
                break;
              case 'requiredField':
                validators.checkRequiredField(stage, validator, config, messages);
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
  }).name;
