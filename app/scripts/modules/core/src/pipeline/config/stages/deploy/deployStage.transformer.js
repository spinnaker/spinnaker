'use strict';

import _ from 'lodash';

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_TRANSFORMER =
  'spinnaker.core.pipeline.stage.deploy.transformer';
export const name = CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_TRANSFORMER; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_TRANSFORMER, [])
  .service('deployStageTransformer', function() {
    /**
     * Removes rollingPush, modifyAsgLaunchConfiguration stages, adding them as tasks to the parent deploy stage,
     * then overriding the status and endTime fields for the deploy stage
     */
    function transformRollingPushes(execution) {
      var stagesToRemove = [];
      execution.stages.forEach(function(stage) {
        if (
          stage.type === 'deploy' &&
          stage.context &&
          stage.context.strategy === 'rollingpush' &&
          stage.context.source
        ) {
          var modifyLaunchConfigurationStage = _.find(execution.stages, {
            type: 'modifyAsgLaunchConfiguration',
            parentStageId: stage.id,
          });
          var rollingPushStage = _.find(execution.stages, {
            type: 'rollingPush',
            parentStageId: stage.id,
          });
          if (modifyLaunchConfigurationStage) {
            stagesToRemove.push(modifyLaunchConfigurationStage);
            stage.tasks.push({
              name: modifyLaunchConfigurationStage.name,
              startTime: modifyLaunchConfigurationStage.startTime,
              endTime: modifyLaunchConfigurationStage.endTime,
              status: modifyLaunchConfigurationStage.status,
            });
            stage.endTime = modifyLaunchConfigurationStage.endTime;
            stage.status = modifyLaunchConfigurationStage.status;
          }
          if (rollingPushStage) {
            stagesToRemove.push(rollingPushStage);
            stage.tasks.push({
              name: rollingPushStage.name,
              startTime: rollingPushStage.startTime,
              endTime: rollingPushStage.endTime,
              status: rollingPushStage.status,
            });
            stage.endTime = rollingPushStage.endTime;
            stage.status = rollingPushStage.status;
          }
        }
      });
      execution.stages = execution.stages.filter(function(stage) {
        return !stagesToRemove.includes(stage);
      });
    }

    this.transform = function(application, execution) {
      transformRollingPushes(execution);
    };
  });
