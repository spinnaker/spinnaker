'use strict';

/* eslint consistent-return:0 */

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cluster.service', [
  require('../naming/naming.service.js'),
  require('../api/api.service'),
  require('../utils/lodash.js'),
  require('../serverGroup/serverGroup.transformer.js'),
])
  .factory('clusterService', function ($q, API, _, serverGroupTransformer, namingService) {

    function loadServerGroups(applicationName) {
      var serverGroupLoader = $q.all({
        serverGroups: API.one('applications').one(applicationName).all('serverGroups').getList().then(g => g, () => []),
      });
      return serverGroupLoader.then(function(results) {
        results.serverGroups = results.serverGroups || [];

        results.serverGroups.forEach(addHealthStatusCheck);
        results.serverGroups.forEach(parseName);
        results.serverGroups.forEach((serverGroup) =>
            serverGroup.category = 'serverGroup'
          );

        return $q.all(results.serverGroups.map(serverGroupTransformer.normalizeServerGroup));
      });
    }

    function parseName(serverGroup) {
      var nameParts = namingService.parseServerGroupName(serverGroup.name);
      serverGroup.stack = nameParts.stack;
      serverGroup.detail = nameParts.freeFormDetails;
    }

    function addHealthStatusCheck(serverGroup) {
      serverGroup.instances.forEach(function (instance) {
        var healthList = instance.health;
        var activeHealth = _.filter(healthList, function(health) {
          return health.state !== 'Unknown';
        });

        instance.hasHealthStatus = Boolean(activeHealth.length);
      });
    }

    function addHealthCountsToCluster(cluster) {
      cluster.instanceCounts = {
        up: 0,
        down: 0,
        unknown: 0,
        starting: 0,
        outOfService: 0,
        succeeded: 0,
        failed: 0,
        total: 0,
      };
      var operand = cluster.serverGroups || [];
      operand.forEach(function(serverGroup) {
        if (!serverGroup.instanceCounts) {
          return;
        }
        cluster.instanceCounts.total += serverGroup.instanceCounts.total || 0;
        cluster.instanceCounts.up += serverGroup.instanceCounts.up || 0;
        cluster.instanceCounts.down += serverGroup.instanceCounts.down || 0;
        cluster.instanceCounts.unknown += serverGroup.instanceCounts.unknown || 0;
        cluster.instanceCounts.starting += serverGroup.instanceCounts.starting || 0;
        cluster.instanceCounts.outOfService += serverGroup.instanceCounts.outOfService || 0;
        cluster.instanceCounts.succeeded += serverGroup.instanceCounts.succeeded || 0;
        cluster.instanceCounts.failed += serverGroup.instanceCounts.failed || 0;
      });
    }

    function baseTaskMatcher(task, serverGroup) {
      var taskRegion = task.getValueFor('regions') ? task.getValueFor('regions')[0] : task.getValueFor('region') || null;
      return serverGroup.account === task.getValueFor('credentials') &&
        serverGroup.region === taskRegion &&
        serverGroup.name === task.getValueFor('asgName');
    }

    function instanceIdsTaskMatcher(task, serverGroup) {
      if (task.getValueFor('region') === serverGroup.region && task.getValueFor('credentials') === serverGroup.account) {
        if (task.getValueFor('knownInstanceIds')) {
          return _.intersection(_.pluck(serverGroup.instances, 'id'), task.getValueFor('knownInstanceIds')).length > 0;
        } else {
          return _.intersection(_.pluck(serverGroup.instances, 'id'), task.getValueFor('instanceIds')).length > 0;
        }
      }
      return false;
    }

    var taskMatchers = {
      'createcopylastasg': function(task, serverGroup) {
        var source = task.getValueFor('source');
        var targetAccount = task.getValueFor('deploy.account.name');
        var targetRegion = task.getValueFor('availabilityZones') ? Object.keys(task.getValueFor('availabilityZones'))[0] : null;
        var dsgs = task.getValueFor('deploy.server.groups');
        var targetServerGroup = targetRegion && dsgs && dsgs[targetRegion] ? dsgs[targetRegion][0] : null;
        var sourceServerGroup = source.asgName;
        var sourceAccount = source.account;
        var sourceRegion = source.region;

        if (serverGroup.account === targetAccount && serverGroup.region === targetRegion && serverGroup.name === targetServerGroup) {
          return true;
        }
        if (serverGroup.account === sourceAccount && serverGroup.region === sourceRegion && serverGroup.name === sourceServerGroup) {
          return true;
        }
        return false;
      },
      'createdeploy': function(task, serverGroup) {
        var account = task.getValueFor('deploy.account.name'),
            region = task.getValueFor('deploy.server.groups') ? Object.keys(task.getValueFor('deploy.server.groups'))[0] : null,
            serverGroupName = (serverGroup && region) ? task.getValueFor('deploy.server.groups')[region][0] : null;

        if (account && serverGroup && region) {
          return serverGroup.account === account && serverGroup.region === region && serverGroup.name === serverGroupName;
        }
        return false;
      },
      'enableinstancesindiscovery': instanceIdsTaskMatcher,
      'disableinstancesindiscovery': instanceIdsTaskMatcher,
      'disableinstances': instanceIdsTaskMatcher,
      'registerinstanceswithloadbalancer': instanceIdsTaskMatcher,
      'deregisterinstancesfromloadbalancer': instanceIdsTaskMatcher,
      'terminateinstances': instanceIdsTaskMatcher,
      'rebootinstances': instanceIdsTaskMatcher,
      'resizeasg': baseTaskMatcher,
      'resizeservergroup': baseTaskMatcher,
      'disableasg': baseTaskMatcher,
      'disableservergroup': baseTaskMatcher,
      'destroyasg': baseTaskMatcher,
      'destroyservergroup': baseTaskMatcher,
      'enableasg': baseTaskMatcher,
      'enableservergroup': baseTaskMatcher,
      'enablegoogleservergroup': baseTaskMatcher,
      'disablegoogleservergroup': baseTaskMatcher,
      'resumeasgprocessesdescription': baseTaskMatcher, // fun fact, this is how an AWS resize starts
      'rollbackServerGroup': function(task, serverGroup) {
        var account = task.getValueFor('credentials'),
            region = task.getValueFor('regions') ? task.getValueFor('regions')[0] : null;

        if (account && serverGroup.account === account && region && serverGroup.region === region) {
          return serverGroup.name === task.getValueFor('targetop.asg.disableServerGroup.name') ||
            serverGroup.name === task.getValueFor('targetop.asg.enableServerGroup.name');
        }
        return false;
      },
    };

    function taskMatches(task, serverGroup) {
      let notificationType = _.has(task, 'execution.stages') ?
        task.execution.stages[0].context['notification.type'] ?
          task.execution.stages[0].context['notification.type'] :
          task.execution.stages[0].type : // TODO: good grief
        task.getValueFor('notification.type');
      var matcher = taskMatchers[notificationType];
      return matcher ? matcher(task, serverGroup) : false;
    }

    function addTasksToServerGroups(application) {
      let runningTasks = application.runningTasks.data || [];
      if (!application.serverGroups.data) {
        return; // still run if there are no running tasks, since they may have all finished and we need to clear them.
      }
      application.serverGroups.data.forEach(function(serverGroup) {
        if (!serverGroup.runningTasks) {
          serverGroup.runningTasks = [];
        }
        serverGroup.runningTasks.length = 0;
        runningTasks.forEach(function(task) {
          if (taskMatches(task, serverGroup)) {
            serverGroup.runningTasks.push(task);
          }
        });
      });
    }

    function findStagesWithServerGroupInfo(stages) {
      var stagesWithServerGroups = _.filter(stages, function (stage) {
         return ( _.includes(['deploy', 'destroyAsg', 'resizeAsg'], stage.type) && _.has(stage.context, 'deploy.server.groups') ) ||
           ( stage.type === 'disableAsg' && _.has(stage.context, 'targetop.asg.disableAsg.name') );
      });

      return stagesWithServerGroups;
    }

    function extractServerGroupNameFromContext(context) {
      return _.first(_.values(context['deploy.server.groups'])) || context['targetop.asg.disableAsg.name'] || undefined;
    }

    function extractRegionFromContext(context) {
      return _.first(_.keys(context['deploy.server.groups'])) || _.first(context['targetop.asg.disableAsg.regions']) || undefined;
    }

    function addExecutionsToServerGroups(application) {
      let executions = application.runningExecutions.data || [];

      if(!application.serverGroups.data) {
        return; // still run if there are no running tasks, since they may have all finished and we need to clear them.
      }

      application.serverGroups.data.forEach(function(serverGroup) {
        serverGroup.executions = [];
        executions.forEach(function (execution) {

          var stages = findStagesWithServerGroupInfo(execution.stages);

          _.forEach(stages, function(stage) {
            var stageServerGroup = stage ? extractServerGroupNameFromContext(stage.context) : undefined;
            var stageAccount = stage && stage.context ? stage.context.account || stage.context.credentials : undefined;
            var stageRegion = stage ? extractRegionFromContext(stage.context) : undefined;

            if(_.includes(stageServerGroup, serverGroup.name) &&
              stageAccount === serverGroup.account &&
              stageRegion === serverGroup.region) {
              serverGroup.executions.push(execution);
            }

          });
        });
      });

      return application;
    }



    function addProvidersAndServerGroupsToInstances(serverGroups) {
      serverGroups.forEach(function(serverGroup) {
        serverGroup.instances.forEach(function(instance) {
          instance.provider = serverGroup.type || serverGroup.provider;
          instance.serverGroup = instance.serverGroup || serverGroup.name;
          instance.vpcId = serverGroup.vpcId;
        });
      });
    }

    function collateServerGroupsIntoClusters(serverGroups) {
      var clusters = [];
      var groupedByAccount = _.groupBy(serverGroups, 'account');
      _.forOwn(groupedByAccount, function(accountServerGroups, account) {
        var groupedByCategory = _.groupBy(accountServerGroups, 'category');
        _.forOwn(groupedByCategory, function(categoryServerGroups, category) {
          var groupedByCluster = _.groupBy(categoryServerGroups, 'cluster');
          _.forOwn(groupedByCluster, function(clusterServerGroups, clusterName) {
            var cluster = {account: account, category: category, name: clusterName, serverGroups: clusterServerGroups};
            addHealthCountsToCluster(cluster);
            clusters.push(cluster);
          });
        });
      });
      addProvidersAndServerGroupsToInstances(serverGroups);
      return clusters;
    }

    function updateLoadBalancers(application) {
      application.serverGroups.data.forEach(function(serverGroup) {
        serverGroup.loadBalancers = application.loadBalancers.data.filter(function(loadBalancer) {
          return loadBalancer.serverGroups.some(function(loadBalancerGroup) {
            return loadBalancerGroup.name === serverGroup.name &&
              loadBalancer.region === serverGroup.region &&
              loadBalancer.account === serverGroup.account;
          });
        }).map(function(loadBalancer) { return loadBalancer.name; });
      });
    }

    function normalizeServerGroupsWithLoadBalancers(application) {
      updateLoadBalancers(application);
    }

    function getCluster(application, account, cluster) {
      return API.one('applications').one(application).one('clusters', account).one(cluster).get();
    }

    function getClusters(application) {
      return API.one('applications').one(application).one('clusters').get();
    }

    function addServerGroupsToApplication(application, serverGroups = []) {
      if (application.serverGroups.data) {
        // remove any that have dropped off, update any that have changed
        let toRemove = [];
        application.serverGroups.data.forEach((serverGroup, idx) => {
          let matches = serverGroups.filter((test) =>
            test.name === serverGroup.name &&
            test.account === serverGroup.account &&
            test.region === serverGroup.region &&
            test.category === serverGroup.category
          );
          if (!matches.length) {
            toRemove.push(idx);
          } else {
            if (serverGroup.stringVal && matches[0].stringVal && serverGroup.stringVal !== matches[0].stringVal) {
              application.serverGroups.data[idx] = matches[0];
            }
          }
        });

        toRemove.forEach((idx) => application.serverGroups.data.splice(idx, 1));

        // add any new ones
        serverGroups.forEach((serverGroup) => {
          if (!application.serverGroups.data.filter((test) =>
              test.name === serverGroup.name &&
              test.account === serverGroup.account &&
              test.region === serverGroup.region &&
              test.category === serverGroup.category
            ).length) {
            application.serverGroups.data.push(serverGroup);
          }
        });
      } else {
        application.serverGroups.data = serverGroups;
      }
    }

    return {
      loadServerGroups: loadServerGroups,
      createServerGroupClusters: collateServerGroupsIntoClusters,
      normalizeServerGroupsWithLoadBalancers: normalizeServerGroupsWithLoadBalancers,
      addTasksToServerGroups: addTasksToServerGroups,
      addExecutionsToServerGroups: addExecutionsToServerGroups,
      addServerGroupsToApplication: addServerGroupsToApplication,

      //for testing purposes only
      extractRegionFromContext: extractRegionFromContext,
      getCluster: getCluster,
      getClusters: getClusters,
    };

  });
