'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.transformer', [
  require('../../../../core/utils/lodash.js'),
])
  .service('canaryStageTransformer', function($log, _) {

    // adds "canary" or "baseline" to the deploy stage name when converting it to a task
    function getDeployTaskName(stage) {
      if (stage.context.freeFormDetails) {
        var nameParts = stage.name.split(' ');
        if (_.endsWith(stage.context.freeFormDetails, 'canary')) {
          nameParts.splice(1, 0, 'canary');
        } else {
          nameParts.splice(1, 0, 'baseline');
        }
        return nameParts.join(' ');
      }
      return stage.name;
    }

    function getException (stage) {
      if (stage.context) {
        if (stage.context.exception && stage.context.exception.details.errors.length) {
          return stage.context.exception.details.errors.join(', ');
        }
        if (stage.context['kato.tasks'] && stage.context['kato.tasks'].length) {
          var lastTask = stage.context['kato.tasks'][stage.context['kato.tasks'].length-1];
          return lastTask.exception ? lastTask.exception.message : null;
        }
      }
      return null;
    }

    function buildCanaryDeploymentsFromClusterPairs(stage) {
      return _.map(stage.context.clusterPairs, function (pair) {
        var name = function (cluster) {
          var parts = [cluster.application];
          if (cluster.stack) {
            parts.push(cluster.stack);
          } else if (cluster.freeFormDetails) {
            parts.push('');
          }
          if (cluster.freeFormDetails) {
            parts.push(cluster.freeFormDetails);
          }
          return parts.join('-');
        };
        var region = function (cluster) {
          return _.first(_.keys(cluster.availabilityZones));
        };
        return {
          canaryCluster: {
            accountName: pair.canary.account,
            imageId: null,
            buildId: null,
            name: name(pair.canary),
            region: region(pair.canary),
            type: 'aws',
          },
          baselineCluster: {
            accountName: pair.baseline.account,
            imageId: pair.baseline.amiName,
            buildId: pair.baseline.buildUrl,
            name: name(pair.baseline),
            region: region(pair.baseline),
            type: 'aws',
          }
        };
      });
    }

    function extractBuild(cluster) {
      cluster.build = cluster.build || {};
      if (cluster.buildId) {
        var parts = cluster.buildId.split('/');
        parts.pop();
        cluster.build.url = cluster.buildId;
        cluster.build.number = parts.pop();
      } else {
        cluster.build.url = '#';
        cluster.build.number = 'n/a';
      }
    }

    function createSyntheticCanaryDeploymentStage(stage, deployment, status, deployParent, deploymentEndTime, canaryDeploymentId, execution) {
      return {
        parentStageId: stage.id,
        syntheticStageOwner: 'STAGE_AFTER',
        id: stage.id + '-' + deployment.id,
        type: 'canaryDeployment',
        name: deployment.canaryCluster.region,
        status: status,
        startTime: deployParent.startTime,
        endTime: deploymentEndTime,
        context: {
          commits: deployment.commits,
          canaryDeploymentId: canaryDeploymentId,
          application: stage.context.canary.application || execution.application,
          canaryCluster: deployment.canaryCluster,
          baselineCluster: deployment.baselineCluster,
          canaryResult: deployment.canaryResult,
          status: {
            reportUrl: deployment.canaryResult.canaryReportURL,
            score: deployment.canaryResult.score,
            result: deployment.canaryResult.result,
            duration: deployment.canaryResult.timeDuration,
            health: deployment.health ? deployment.health.health : '',
          }
        }
      };
    }

    this.transform = function(application, execution) {
      var syntheticStagesToAdd = [];
      execution.stages.forEach(function(stage) {
        if (stage.type === 'canary') {

          stage.exceptions = [];

          var deployParent = _.find(execution.stages, {
            type: 'deployCanary',
            context: {
              canaryStageId: stage.id,
            }
          });
          if (!deployParent) {
            $log.warn('No deployment parent found for canary stage in execution:', execution.id);
            return;
          }
          var monitorStage = _.find(execution.stages, {
            type: 'monitorCanary',
            context: {
              canaryStageId: stage.id,
            }
          });

          var deployStages = _.filter(execution.stages, {
            type: 'deploy',
            parentStageId: deployParent.id,
          });

          if (getException(monitorStage)) {
            stage.exceptions.push('Monitor Canary failure: ' + getException(monitorStage));
          }
          deployStages.forEach((deployStage) => {
            if (getException(deployStage)) {
              stage.exceptions.push(deployStage.name + ': ' + getException(deployStage));
            }
          });

          stage.exceptions = _.uniq(stage.exceptions);

          stage.context.canary = monitorStage.context.canary || deployParent.context.canary || stage.context.canary;
          if (!stage.context.canary.canaryDeployments) {
            stage.context.canary.canaryDeployments = buildCanaryDeploymentsFromClusterPairs(stage);
          }
          var status = monitorStage.status === 'CANCELED' || _.some(deployStages, { status: 'CANCELED' }) ? 'CANCELED' : 'UNKNOWN';
          if (_.some(deployStages, { status: 'RUNNING' })) {
            status = 'RUNNING';
          }
          if (_.some(deployStages, { status: 'TERMINAL' })) {
            status = 'TERMINAL';
          }
          var canaryStatus = stage.context.canary.status;
          if (canaryStatus && status !== 'CANCELED') {
            if (canaryStatus.status === 'LAUNCHED' || monitorStage.status === 'RUNNING') {
              status = 'RUNNING';
            }
            if (canaryStatus.complete) {
              status = 'COMPLETED';
            }
            if (canaryStatus.status === 'DISABLED') {
              status = 'DISABLED';
            }
            if (canaryStatus.status === 'FAILED') {
              status = 'FAILED';
            }
            if (canaryStatus.status === 'TERMINATED') {
              status = 'TERMINATED';
            }
            canaryStatus.status = status;
          } else {
            stage.context.canary.status = { status: status };
          }
          stage.status = status;

          var tasks = _.map(deployStages, function(deployStage) {
            var region = _.first(_.keys(deployStage.context.availabilityZones));
            return {
              id: deployStage.id,
              region: region,
              name: getDeployTaskName(deployStage),
              startTime: deployStage.startTime,
              endTime: deployStage.endTime,
              status: deployStage.status,
              commits: deployStage.context.commits
            };
          });

          tasks.push({
            id: monitorStage.id,
            name: monitorStage.name,
            startTime: monitorStage.startTime,
            endTime: monitorStage.endTime,
            status: monitorStage.status,
          });

          stage.tasks = tasks;

          stage.context.canary.canaryDeployments.forEach(function(deployment, deploymentIdx) {

            deployment.id = deployment.id || deploymentIdx;

            var deployedClusterPair = _.find(monitorStage.context.deployedClusterPairs, {
              baselineCluster: {
                accountName: deployment.baselineCluster.accountName,
                name: deployment.baselineCluster.name,
                region: deployment.baselineCluster.region,
              },
              canaryCluster: {
                accountName: deployment.canaryCluster.accountName,
                name: deployment.canaryCluster.name,
                region: deployment.canaryCluster.region,
              }
            });

            var deploymentEndTime = null;
            var monitorTask = _.find(monitorStage.tasks, { name: 'monitorCanary' });
            if (monitorTask) {
              deploymentEndTime = monitorTask.endTime;
            }

            extractBuild(deployment.baselineCluster);
            extractBuild(deployment.canaryCluster);

            deployment.canaryResult = deployment.canaryAnalysisResult || {};
            deployment.canaryCluster = deployment.canaryCluster || {};

            var foundTask = _.find(stage.tasks, function(task) {
              return task.region === deployment.baselineCluster.region && task.commits !== undefined && task.commits.length > 0;
            });
            if(foundTask !== undefined && foundTask.commits !== undefined) {
              deployment.commits = foundTask.commits;
            }

            if (deployedClusterPair) {
              var canaryServerGroup = _.find(application.serverGroups, {
                name: deployedClusterPair.canaryCluster.serverGroup,
                account: deployedClusterPair.canaryCluster.accountName,
                region: deployedClusterPair.canaryCluster.region
              });
              if (canaryServerGroup) {
                deployment.canaryCluster.capacity = canaryServerGroup.instances.length;
              } else {
                deployment.canaryCluster.capacity = 'n/a';
              }

              var baselineServerGroup = _.find(application.serverGroups, {
                name: deployedClusterPair.baselineCluster.serverGroup,
                account: deployedClusterPair.baselineCluster.accountName,
                region: deployedClusterPair.baselineCluster.region
              });
              if (baselineServerGroup) {
                deployment.baselineCluster.capacity = baselineServerGroup.instances.length;
              } else {
                deployment.baselineCluster.capacity = 'n/a';
              }
            } else {
              deployment.baselineCluster.capacity = 'n/a';
              deployment.canaryCluster.capacity = 'n/a';
            }

            var canaryDeploymentId = deployment.canaryAnalysisResult ? deployment.canaryAnalysisResult.canaryDeploymentId : null;
            syntheticStagesToAdd.push(createSyntheticCanaryDeploymentStage(stage, deployment, status, deployParent, deploymentEndTime, canaryDeploymentId, execution));
          });
          execution.stages = execution.stages.filter((stage) => deployStages.indexOf(stage) === -1);
        }
      });
      execution.stages = execution.stages.concat(syntheticStagesToAdd);
    };
  });
