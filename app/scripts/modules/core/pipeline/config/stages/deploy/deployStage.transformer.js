'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.deploy.transformer', [
  require('../../../../utils/lodash.js'),
])
  .service('deployStageTransformer', function(_) {

    /**
     * Removes rollingPush, modifyAsgLaunchConfiguration stages, adding them as tasks to the parent deploy stage,
     * then overriding the status and endTime fields for the deploy stage
     */
    function transformRollingPushes(execution) {
      var stagesToRemove = [];
      execution.stages.forEach(function(stage) {
        if (stage.type === 'deploy' && stage.context && stage.context.strategy === 'rollingpush' && stage.context.source) {
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
        return stagesToRemove.indexOf(stage) === -1;
      });
    }

    this.transform = function(application, execution) {
      transformRollingPushes(execution);
    };
  });
