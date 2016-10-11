'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.validator.service', [
  require('../pipelineConfigProvider.js'),
  require('../services/pipelineConfigService.js'),
  require('core/naming/naming.service.js'),
])
  .factory('pipelineConfigValidator', function($log, pipelineConfig, pipelineConfigService, namingService, $q) {

    // Stores application pipeline configs so we don't needlessly fetch them every time we validate the pipeline
    let pipelineCache = new Map();

    let clearCache = () => {
      pipelineCache.clear();
    };

    let addTriggers = (pipelines, pipelineIdToFind, stagesToTest) => {
      let [match] = pipelines.filter(p => p.id === pipelineIdToFind);
      if (match) {
        stagesToTest.push(...match.triggers);
      }
    };

    function addExternalTriggers(trigger, stagesToTest, deferred) {
      pipelineConfigService.getPipelinesForApplication(trigger.application).then(pipelines => {
        pipelineCache.set(trigger.application, pipelines);
        addTriggers(pipelines, trigger.pipeline, stagesToTest);
        deferred.resolve();
      });
    }

    function addPipelineTriggers(pipeline, stagesToTest) {
      let pipelineTriggers = pipeline.triggers.filter(t => t.type === 'pipeline');
      let parentTriggersToCheck = [];
      pipelineTriggers.forEach(trigger => {
        let deferred = $q.defer();
        if (pipelineCache.has(trigger.application)) {
          addTriggers(pipelineCache.get(trigger.application), trigger.pipeline, stagesToTest);
        } else {
          addExternalTriggers(trigger, stagesToTest, deferred);
          parentTriggersToCheck.push(deferred.promise);
        }
      });
      return parentTriggersToCheck;
    }

    var validators = {
      stageOrTriggerBeforeType: function(pipeline, index, validationConfig, messages) {
        var stageTypes = validationConfig.stageTypes || [validationConfig.stageType];
        var stagesToTest = pipeline.stages.slice(0, index + 1);
        if (pipeline.parallel) {
          stagesToTest = pipelineConfigService.getAllUpstreamDependencies(pipeline, pipeline.stages[index]);
        }
        stagesToTest = stagesToTest.concat(pipeline.triggers);

        var parentTriggersToCheck = validationConfig.checkParentTriggers ? addPipelineTriggers(pipeline, stagesToTest) : [];
        return $q.all(parentTriggersToCheck).then(() => {
          if (stagesToTest.every((stage) => stageTypes.indexOf(stage.type) === -1)) {
            messages.push(validationConfig.message);
          }
        });
      },
      stageBeforeType: function(pipeline, index, validationConfig, messages) {
        if (pipeline.strategy === true && pipeline.stages[index].type === 'deploy') {
          return;
        }

        var stageTypes = validationConfig.stageTypes || [validationConfig.stageType];
        var stagesToTest = pipeline.stages.slice(0, index + 1);
        if (pipeline.parallel) {
          stagesToTest = pipelineConfigService.getAllUpstreamDependencies(pipeline, pipeline.stages[index]);
        }
        if (stagesToTest.every((stage) => stageTypes.indexOf(stage.type) === -1)) {
          messages.push(validationConfig.message);
        }
      },
      checkRequiredField: function(pipeline, stage, validationConfig, config, messages) {
        if (pipeline.strategy === true && ['cluster', 'regions', 'zones', 'credentials'].indexOf(validationConfig.fieldName) > -1) {
          return;
        }

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
            stagesToTest = pipeline.stages.slice(0, index + 1),
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
                if (clusterName === stage.cluster && cluster.account === stage.credentials && cluster.availabilityZones && cluster.availabilityZones.hasOwnProperty(region)) {
                  regionFound = true;
                }
              });
            } else if (toTest.type === 'cloneServerGroup' && toTest.targetCluster === stage.cluster && toTest.region === region) {
              regionFound = true;
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
          parameters = pipeline.parameterConfig || [],
          messages = [],
          asyncValidations = [];

      triggers.forEach(function(trigger, index) {
        let config = pipelineConfig.getTriggerConfig(trigger.type);
        if (config && config.validators) {
          config.validators.forEach(function(validator) {
            switch(validator.type) {
              case 'requiredField':
                validators.checkRequiredField(pipeline, trigger, validator, config, messages);
                break;
              default:
                $log.warn('No validator of type "' + validator.type + '" found, ignoring validation on trigger "' + (index + 1) + '" (' + trigger.type + ')');
            }
          });
        }
      });
      stages.forEach(function(stage, index) {
        let config = pipelineConfig.getStageConfig(stage);
        if (config && config.validators) {
          config.validators.forEach(function(validator) {
            if (validator.skipValidation && validator.skipValidation(pipeline, stage)) {
              return;
            }
            switch(validator.type) {
              case 'stageOrTriggerBeforeType':
                asyncValidations.push(validators.stageOrTriggerBeforeType(pipeline, index, validator, messages));
                break;
              case 'stageBeforeType':
                validators.stageBeforeType(pipeline, index, validator, messages);
                break;
              case 'targetImpedance':
                validators.targetImpedance(pipeline, index, validator, messages);
                break;
              case 'requiredField':
                validators.checkRequiredField(pipeline, stage, validator, config, messages);
                break;
              case 'custom':
                validator.validator(pipeline, stage, validator, config, messages);
                break;
              default:
                $log.warn('No validator of type "' + validator.type + '" found, ignoring validation on stage "' + stage.name + '" (' + stage.type + ')');
            }
          });
        }
      });
      parameters.forEach(function (parameter) {
        var validationConfig = {
          type: 'requiredField',
          fieldName: 'name',
          message: '<b>Name</b> is a required field for parameters.',
        };

        validators.checkRequiredField(pipeline, parameter, validationConfig, {}, messages);
      });
      return $q.all(asyncValidations).then(() => {
        return _.uniq(messages);
      });
    }

    return {
      validatePipeline: validatePipeline,
      clearCache: clearCache,
    };
  });
