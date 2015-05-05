'use strict';

angular.module('deckApp.pipelines.stage.canary.transformer', [])
  .service('canaryStageTransformer', function() {
    this.transform = function(execution) {
      var syntheticStagesToAdd = [];
      execution.stages.forEach(function(stage) {
        if (stage.type === 'canary' && stage.context && stage.context.canary && stage.context.canary.canaryDeployments) {
          var status = 'UNKNOWN';
          var canaryStatus = stage.context.canary.status;
          if (canaryStatus) {
            if (canaryStatus.status === 'LAUNCHED') {
              status = 'RUNNING';
            }
            if (canaryStatus.complete) {
              status = 'COMPLETED';
            }
            canaryStatus.status = status;
          }

          stage.context.canary.canaryDeployments.forEach(function(deployment, deploymentIndex) {
            if (!deployment.baselineCluster || !stage.context.clusterPairs[deploymentIndex]) {
              return;
            }
            var clusterPair = stage.context.clusterPairs[deploymentIndex];

            // TODO: Clean this up on the backend - this is a mess
            var baselineBuildParts = deployment.baselineCluster.buildId.split('/');
            baselineBuildParts.pop();
            var baselineBuildNumber = baselineBuildParts.pop();

            var canaryBuildParts = deployment.canaryCluster.buildId.split('/');
            canaryBuildParts.pop();
            var canaryBuildNumber = canaryBuildParts.pop();

            deployment.canaryResult = deployment.canaryAnalysisResult || {};
            deployment.canaryCluster = deployment.canaryCluster || {};

            deployment.canaryCluster.capacity = clusterPair.canary.capacity;
            deployment.baselineCluster.capacity = clusterPair.baseline.capacity;
            deployment.baselineCluster.build = {
              url: deployment.baselineCluster.buildId,
              number: baselineBuildNumber,
            };
            deployment.canaryCluster.build = {
              url: deployment.canaryCluster.buildId,
              number: canaryBuildNumber,
            };

            syntheticStagesToAdd.push({
              parentStageId: stage.id,
              syntheticStageOwner: 'STAGE_BEFORE',
              id: stage.id + '-' + deploymentIndex,
              type: 'canaryDeployment',
              name: deployment.canaryCluster.region,
              status: status,
              startTime: stage.startTime,
              endTime: stage.endTime,
              context: {
                application: stage.context.canary.application,
                canaryCluster: deployment.canaryCluster,
                baselineCluster: deployment.baselineCluster,
                canaryResult: deployment.canaryResult,
                lastUpdated: deployment.lastUpdated,
                status: {
                  reportUrl: deployment.canaryResult.canaryReportURL,
                  score: deployment.canaryResult.score,
                  result: deployment.canaryResult.result,
                  duration: deployment.canaryResult.timeDuration,
                  health: deployment.health ? deployment.health.health : '',
                }
              }
            });
          });
        }
      });
      execution.stages = execution.stages.concat(syntheticStagesToAdd);
    };
  });
