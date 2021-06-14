'use strict';

import { module } from 'angular';
import _ from 'lodash';
import { $log } from 'ngimport';

import { OrchestratedItemTransformer } from '@spinnaker/core';

export const CANARY_CANARY_CANARYSTAGE_TRANSFORMER = 'spinnaker.canary.transformer';
export const name = CANARY_CANARY_CANARYSTAGE_TRANSFORMER; // for backwards compatibility
module(CANARY_CANARY_CANARYSTAGE_TRANSFORMER, []).service('canaryStageTransformer', function () {
  // adds "canary" or "baseline" to the deploy stage name when converting it to a task
  function getDeployTaskName(stage) {
    if (stage.context.freeFormDetails) {
      const nameParts = stage.name.split(' ');
      if (_.endsWith(stage.context.freeFormDetails, 'canary')) {
        nameParts.splice(1, 0, 'canary');
      } else {
        nameParts.splice(1, 0, 'baseline');
      }
      return nameParts.join(' ');
    }
    return stage.name;
  }

  function getException(stage) {
    OrchestratedItemTransformer.defineProperties(stage);
    if (stage.isFailed && _.has(stage, 'context.canary.canaryResult')) {
      const result = stage.context.canary.canaryResult;
      if (result.overallResult === 'FAILURE' && result.message) {
        return `Canary terminated by user. Reason: ${result.message}`;
      }
    }

    const exception = stage.context.exception;
    if (exception && exception.details && exception.details.errors && exception.details.errors.length) {
      return exception.details.errors.join(', ');
    }

    return stage.isFailed ? stage.failureMessage : null;
  }

  function buildCanaryDeploymentsFromClusterPairs(stage) {
    return _.map(stage.context.clusterPairs, function (pair) {
      const name = function (cluster) {
        const parts = [cluster.application];
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
      const region = function (cluster) {
        return _.head(_.keys(cluster.availabilityZones));
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
        },
      };
    });
  }

  function extractBuild(cluster) {
    cluster.build = cluster.build || {};
    if (cluster.buildId) {
      const parts = cluster.buildId.split('/');
      parts.pop();
      cluster.build.url = cluster.buildId;
      cluster.build.number = parts.pop();
    }
  }

  function createSyntheticCanaryDeploymentStage(
    stage,
    deployment,
    status,
    deployParent,
    deploymentEndTime,
    canaryDeploymentId,
    execution,
  ) {
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
        },
      },
    };
  }

  this.transform = function (application, execution) {
    const syntheticStagesToAdd = [];
    if (!execution.hydrated) {
      // don't bother trying to transform if it isn't hydrated
      return;
    }
    execution.stages.forEach(function (stage) {
      if (stage.type === 'canary') {
        OrchestratedItemTransformer.defineProperties(stage);
        stage.exceptions = [];

        const deployParent = _.find(execution.stages, {
          type: 'deployCanary',
          context: {
            canaryStageId: stage.id,
          },
        });
        if (!deployParent) {
          $log.warn('No deployment parent found for canary stage in execution:', execution.id);
          return;
        }

        const monitorStage = _.find(execution.stages, {
          type: 'monitorCanary',
          context: {
            canaryStageId: stage.id,
          },
        });
        if (!monitorStage) {
          $log.warn('No monitorCanary stage found for canary stage in execution:', execution.id);
          return;
        }

        const deployStages = execution.stages.filter(
          (s) => s.parentStageId === deployParent.id && ['deploy', 'createServerGroup'].includes(s.type),
        );

        if (getException(monitorStage)) {
          stage.exceptions.push('Monitor Canary failure: ' + getException(monitorStage));
        }

        if (getException(deployParent)) {
          stage.exceptions.push('Deploy Canary failure: ' + getException(deployParent));
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
        let status =
          monitorStage.status === 'CANCELED' || _.some(deployStages, { status: 'CANCELED' }) ? 'CANCELED' : 'UNKNOWN';

        if (monitorStage.status === 'STOPPED') {
          status = 'STOPPED';
        }

        if (_.some(deployStages, { status: 'RUNNING' })) {
          status = 'RUNNING';
        }
        if (_.some(deployStages, { status: 'TERMINAL' })) {
          status = 'TERMINAL';
        }
        if (_.some(deployStages, { status: 'SKIPPED' })) {
          status = 'SKIPPED';
        }
        const canaryStatus = stage.context.canary.status;
        if (canaryStatus && !['CANCELED', 'STOPPED'].includes(status)) {
          if (canaryStatus.status === 'LAUNCHED' || monitorStage.status === 'RUNNING') {
            status = 'RUNNING';
          }
          if (canaryStatus.complete) {
            status = 'SUCCEEDED';
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

        const tasks = _.map(deployStages, function (deployStage) {
          const region = _.head(_.keys(deployStage.context.availabilityZones));
          return {
            id: deployStage.id,
            region: region,
            name: getDeployTaskName(deployStage),
            startTime: deployStage.startTime,
            endTime: deployStage.endTime,
            status: deployStage.status,
            commits: deployStage.context.commits,
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

        const deployments = stage.context.canary.canaryDeployments.filter((d) => d.baselineCluster && d.canaryCluster);
        deployments.forEach(function (deployment, deploymentIdx) {
          deployment.id = deployment.id || deploymentIdx;

          const deployedClusterPair = _.find(monitorStage.context.deployedClusterPairs, {
            baselineCluster: {
              accountName: deployment.baselineCluster.accountName,
              name: deployment.baselineCluster.name,
              region: deployment.baselineCluster.region,
            },
            canaryCluster: {
              accountName: deployment.canaryCluster.accountName,
              name: deployment.canaryCluster.name,
              region: deployment.canaryCluster.region,
            },
          });

          let deploymentEndTime = null;
          const monitorTask = _.find(monitorStage.tasks, { name: 'monitorCanary' });
          if (monitorTask) {
            deploymentEndTime = monitorTask.endTime;
          }

          extractBuild(deployment.baselineCluster);
          extractBuild(deployment.canaryCluster);

          deployment.canaryResult = deployment.canaryAnalysisResult || {};
          deployment.canaryCluster = deployment.canaryCluster || {};

          const foundTask = _.find(stage.tasks, function (task) {
            return (
              task.region === deployment.baselineCluster.region && task.commits !== undefined && task.commits.length > 0
            );
          });
          if (foundTask !== undefined && foundTask.commits !== undefined) {
            deployment.commits = foundTask.commits;
          }

          // verify the server groups are present - if this is a project-level pipeline, the application will be an
          // empty object
          if (
            deployedClusterPair &&
            deployedClusterPair.baselineCluster &&
            deployedClusterPair.canaryCluster &&
            application.serverGroups
          ) {
            const canaryServerGroup = _.find(application.serverGroups.data, {
              name: deployedClusterPair.canaryCluster.serverGroup,
              account: deployedClusterPair.canaryCluster.accountName,
              region: deployedClusterPair.canaryCluster.region,
            });
            if (canaryServerGroup) {
              deployment.canaryCluster.capacity = canaryServerGroup.instances.length;
            } else {
              deployment.canaryCluster.capacity = 'n/a';
            }

            const baselineServerGroup = _.find(application.serverGroups.data, {
              name: deployedClusterPair.baselineCluster.serverGroup,
              account: deployedClusterPair.baselineCluster.accountName,
              region: deployedClusterPair.baselineCluster.region,
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

          const canaryDeploymentId = deployment.canaryAnalysisResult
            ? deployment.canaryAnalysisResult.canaryDeploymentId
            : null;
          syntheticStagesToAdd.push(
            createSyntheticCanaryDeploymentStage(
              stage,
              deployment,
              status,
              deployParent,
              deploymentEndTime,
              canaryDeploymentId,
              execution,
            ),
          );
        });
        execution.stages = execution.stages.filter((stage) => !deployStages.includes(stage));
      }
    });
    execution.stages = execution.stages.concat(syntheticStagesToAdd);
  };
});
