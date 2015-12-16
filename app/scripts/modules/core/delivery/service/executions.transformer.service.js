'use strict';

let angular = require('angular');

let executionBarLabelTemplate =  require('../../pipeline/config/stages/core/executionBarLabel.html');

module.exports = angular.module('spinnaker.core.delivery.executionTransformer.service', [
  require('../../orchestratedItem/orchestratedItem.transformer.js'),
  require('../../utils/lodash.js'),
  require('../../pipeline/config/pipelineConfigProvider.js'),
])
  .factory('executionsTransformer', function(orchestratedItemTransformer, _, pipelineConfig) {

    var hiddenStageTypes = ['pipelineInitialization', 'waitForRequisiteCompletion'];

    function transformExecution(application, execution) {
      applyPhasesAndLink(execution);
      pipelineConfig.getExecutionTransformers().forEach(function(transformer) {
        transformer.transform(application, execution);
      });
      var stageSummaries = [];

      execution.context = execution.context || {};
      execution.stages.forEach(function(stage, index) {
        stage.before = stage.before || [];
        stage.after = stage.after || [];
        stage.index = index;
        orchestratedItemTransformer.defineProperties(stage);
        if (stage.tasks && stage.tasks.length) {
          stage.tasks.forEach(orchestratedItemTransformer.defineProperties);
        }
      });

      execution.stages.forEach(function(stage) {
        let context = stage.context || {};
        var owner = stage.syntheticStageOwner;
        var parent = _.find(execution.stages, { id: stage.parentStageId });
        if (parent) {
          if (owner === 'STAGE_BEFORE') {
            parent.before.push(stage);
          }
          if (owner === 'STAGE_AFTER') {
            parent.after.push(stage);
          }
        }
        stage.cloudProvider = context.cloudProvider || context.cloudProviderType;
      });

      execution.stages.forEach(function(stage) {
        if (!stage.syntheticStageOwner && hiddenStageTypes.indexOf(stage.type) === -1) {
          let context = stage.context || {};
          stageSummaries.push({
            name: stage.name,
            id: stage.id,
            masterStage: stage,
            type: stage.type,
            before: stage.before,
            after: stage.after,
            status: stage.status,
            comments: context.comments || null,
            cloudProvider: stage.cloudProvider,
            refId: stage.refId,
            requisiteStageRefIds: stage.requisiteStageRefIds && stage.requisiteStageRefIds[0] === '*' ? [] : stage.requisiteStageRefIds || [],
          });
        }
      });

      orchestratedItemTransformer.defineProperties(execution);

      stageSummaries.forEach(transformStageSummary);
      execution.stageSummaries = stageSummaries;
      execution.currentStages = getCurrentStages(execution);
      addStageWidths(execution);
      addBuildInfo(execution);
    }

    function flattenStages(stages, stage) {
      if (stage.before && stage.before.length) {
        stage.before.forEach(function(beforeStage) {
          flattenStages(stages, beforeStage);
        });
      }
      if (stage.masterStage) {
        stages.push(stage.masterStage);
      } else {
        stages.push(stage);
      }
      if (stage.after && stage.after.length) {
        stage.after.forEach(function(afterStage) {
          flattenStages(stages, afterStage);
        });
      }
      return stages;
    }

    function flattenAndFilter(stage) {
      return flattenStages([], stage)
        .filter(function(stage) {
          return stage.type !== 'initialization' && stage.initializationStage !== true;
        })
        .sort(function(a, b) {
          if (a.syntheticStageOwner === 'STAGE_BEFORE' &&
            b.syntheticStageOwner === 'STAGE_BEFORE' &&
            a.parentStageId === b.parentStageId) {
              if (!a.startTime && !b.startTime) {
                return 0;
              }
              if (!a.startTime) {
                return 1;
              }
              if (!b.startTime) {
                return -1;
              }
              return a.startTime - b.startTime;
          }
          return 0;
        });
    }

    function getCurrentStages(execution) {
      var currentStages = execution.stageSummaries.filter(function(stage) {
        return stage.isRunning;
      });
      // if there are no running stages, find the first enqueued stage
      if (!currentStages.length) {
        var enqueued = execution.stageSummaries.filter(function(stage) {
          return stage.hasNotStarted;
        });
        if (enqueued && enqueued.length) {
          currentStages = [enqueued[0]];
        }
      }
      return currentStages;
    }

    function transformStage(stage) {
      var stages = flattenAndFilter(stage);

      if (!stages.length) {
        return;
      }


      if (stage.masterStage) {
        var lastStage = stages[stages.length - 1];
        stage.startTime = stages[0].startTime;
        var lastNotStartedStage = _(stages).findLast(
          function (childStage) {
            return !childStage.hasNotStarted;
          }
        );

        var lastFailedStage = _(stages).findLast(
          function (childStage) {
            return childStage.isFailed;
          }
        );

        var lastRunningStage = _(stages).findLast(
          function (childStage) {
            return childStage.isRunning;
          }
        );

        var currentStage = lastRunningStage || lastFailedStage || lastNotStartedStage || lastStage;
        stage.status = currentStage.status;
        stage.endTime = currentStage.endTime;
      }
      stage.stages = stages;

    }

    function applyPhasesAndLink(execution) {
      if (!execution.parallel) {
        return;
      }
      var stages = execution.stages;
      var allPhasesResolved = true;
      // remove any invalid requisiteStageRefIds, set requisiteStageRefIds to empty for synthetic stages
      stages.forEach(function (stage) {
        stage.requisiteStageRefIds = stage.requisiteStageRefIds || [];
        stage.requisiteStageRefIds = stage.requisiteStageRefIds.filter(function(parentId) {
          return _.find(stages, { refId: parentId });
        });
      });
      stages.forEach(function (stage) {
        var phaseResolvable = true,
          phase = 0;
        // if there are no dependencies or it's a synthetic stage, set it to 0
        if (stage.phase === undefined && !stage.requisiteStageRefIds.length) {
          stage.phase = phase;
        } else {
          stage.requisiteStageRefIds.forEach(function (parentId) {
            var parent = _.find(stages, { refId: parentId });
            if (parent.phase === undefined) {
              phaseResolvable = false;
            } else {
              phase = Math.max(phase, parent.phase);
            }
          });
          if (phaseResolvable) {
            stage.phase = phase + 1;
          } else {
            allPhasesResolved = false;
          }
        }
      });
      execution.stages = _.sortByAll(stages, 'phase', 'refId');
      if (!allPhasesResolved) {
        applyPhasesAndLink(execution);
      }
    }

    function addStageWidths(execution) {
      execution.stageWidth = 100 / execution.stageSummaries.length + '%';
    }

    function styleStage(stage) {
      var stageConfig = pipelineConfig.getStageConfig(stage);
      if (stageConfig) {
        stage.labelTemplateUrl = stageConfig.executionLabelTemplateUrl || executionBarLabelTemplate;
      }
    }

    function addBuildInfo(execution) {
      var deploymentDetails = _(execution.stages)
        .chain()
        .find(function(stage) {
          return stage.type === 'deploy';
        })
        .get('context')
        .get('deploymentDetails')
        .value();

      // TODO - remove 'deploymentDetails || ...' once we've finalized the migration away from per-stage deploymentDetails
      // var deploymentDetails = execution.context.deploymentDetails;
      deploymentDetails = deploymentDetails || execution.context.deploymentDetails;

      if (deploymentDetails && deploymentDetails.length) {
        execution.buildInfo = deploymentDetails[0].jenkins;
      }
    }

    function transformStageSummary(summary, index) {
      summary.index = index;
      summary.stages = flattenAndFilter(summary);
      summary.stages.forEach(transformStage);
      summary.stages.forEach((stage) => delete stage.stages);
      summary.masterStageIndex = summary.stages.indexOf(summary.masterStage) === -1 ? 0 : summary.stages.indexOf(summary.masterStage);
      transformStage(summary);
      styleStage(summary);
      orchestratedItemTransformer.defineProperties(summary);
    }

    return {
      transformExecution: transformExecution
    };
  });
