'use strict';

angular.module('deckApp.pipelines.stage.canary.transformer', [])
  .service('canaryStageTransformer', function() {
    this.transform = function(execution) {
      var syntheticStagesToAdd = [];
      execution.stages.forEach(function(stage) {
        if (stage.type === 'canary' && stage.context && stage.context.canary && stage.context.canary.canaryDeployments) {
          stage.context.canary.canaryDeployments.forEach(function(deployment, deploymentIndex) {
            syntheticStagesToAdd.push({
              parentStageId: stage.id,
              syntheticStageOwner: 'STAGE_AFTER',
              id: stage.id + '-' + deploymentIndex,
              type: 'canaryDeployment',
              name: deployment.canaryCluster.region,
              status: stage.context.canary.status === 'LAUNCHED' ? 'RUNNING' : 'COMPLETED',
              context: {
                canaryCluster: deployment.canaryCluster,
                baselineCluster: deployment.baselineCluster,
                canaryResult: deployment.canaryResult,
                lastUpdated: deployment.lastUpdated,
                status: {
                  reportUrl: deployment.canaryResult.canaryReportURL,
                  score: deployment.canaryResult.score,
                  result: deployment.canaryResult.result,
                  duration: deployment.canaryResult.timeDuration,
                  health: deployment.health
                }
              }
            });
          });
        }
      });
      execution.stages = execution.stages.concat(syntheticStagesToAdd);
    };
  });
