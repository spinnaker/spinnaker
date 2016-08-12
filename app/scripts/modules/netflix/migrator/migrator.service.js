'use strict';

let angular = require('angular');

module.exports = angular

  .module('spinnaker.migrator.service', [
    require('../../core/utils/lodash.js'),
    require('../../core/task/taskExecutor.js'),
    require('../../core/account/account.service'),
  ])
  .factory('migratorService', function(_, taskExecutor, accountService) {

    function executeMigration(application, config) {
      var taskStarter = taskExecutor.executeTask({
        application: application,
        description: 'Migrate ' + config.name + ' to VPC0',
        job: [config]
      });

      return taskStarter.then((task) => {
        task.getEventLog = () => getEventLog(task);
        task.getPreview = () => getResults(task, config.type);
        task.migrationComplete = () => task.steps.some(t => t.name === 'monitorMigration' && t.status === 'SUCCEEDED');
        return task;
      });
    }

    let getPipelineMigrationResults = (task) => {
      let katoTask = getKatoTask(task);
      if (!katoTask) {
        return {};
      }
      let clusterResults = task.getValueFor('kato.tasks')[0].resultObjects.filter(r => r.cluster);
      let resultSummary = {
        securityGroups: [],
        loadBalancers: []
      };
      let regions = [];
      clusterResults.forEach(clusterResult => {
        let region = Object.keys(clusterResult.cluster.availabilityZones)[0];
        clusterResult.region = region;
        regions.push(region);
        (clusterResult.securityGroupMigrations || []).forEach(m => m.created.forEach(c => c.region = region));
        (clusterResult.loadBalancerMigrations || []).forEach(m => {
          m.accountName = clusterResult.cluster.account;
          m.region = region;
          m.securityGroups.forEach(s => s.created.forEach(c => c.region = region));
        });
        resultSummary.securityGroups = resultSummary.securityGroups.concat(clusterResult.securityGroupMigrations);
        resultSummary.loadBalancers = resultSummary.loadBalancers.concat(clusterResult.loadBalancerMigrations);
        resultSummary.multipleRegions = _.uniq(regions).length > 1;
      });
      return resultSummary;
    };

    let getServerGroupMigrationResults = (task) => {
      return task.getValueFor('kato.tasks')[0].resultObjects.filter(r => r.serverGroupNames)[0];
    };

    let getResults = (task, migrationType) => {
      if (getKatoTask(task)) {
        let resultSummary = migrationType === 'migratePipeline' ? getPipelineMigrationResults(task) : getServerGroupMigrationResults(task);
        let results = {
          serverGroupNames: resultSummary.serverGroupNames,
          securityGroups: getNewSecurityGroups(resultSummary),
          loadBalancers: resultSummary.loadBalancers.filter(lb => !lb.targetExists),
          warnings: getWarnings(resultSummary),
          multipleRegions: resultSummary.multipleRegions
        };
        resultSummary.loadBalancers.forEach(lb => {
          results.securityGroups = results.securityGroups.concat(getNewSecurityGroups(lb));
          results.warnings = results.warnings.concat(getWarnings(lb));
        });
        results.securityGroups = getUniqueGroups(results.securityGroups);
        results.loadBalancers = getUniqueLoadBalancers(results.loadBalancers);
        results.multipleAccounts = getTargetAccounts(resultSummary).length > 1;
        addAccountNames(results);
        return results;
      }
      return {};
    };

    let getTargetAccounts = (resultSummary) => {
      let targetAccounts = resultSummary.securityGroups.map(g => g.target.credentials);
      resultSummary.securityGroups.forEach(g => {
        g.created.forEach(g2 => {
          targetAccounts.push(g2.credentials);
        });
      });
      return _.uniq(targetAccounts);
    };

    let addAccountNames = (results) => {
      accountService.getAllAccountDetailsForProvider('aws').then(accounts => {
        results.securityGroups.forEach(group => {
          let [match] = accounts.filter(a => a.accountId === group.accountId);
          group.accountName = match ? match.name : group.accountId;
        });
      });
    };

    let getNewSecurityGroups = (container) => {
      let results = [];
      if (container.created && container.created.length) {
        results = container.created;
      }
      if (container.securityGroups && container.securityGroups) {
        container.securityGroups.forEach(sg => {
          results = results.concat(sg.created);
        });
      }
      return results;
    };

    // TODO: refactor this once there are other types of warnings coming through
    // The only warnings we care about right now are skipped security groups
    let getWarnings = (container) => {
      let results = [];
      (container.securityGroups || []).forEach(sg => {
        if (sg.warnings && sg.warnings.length) {
          sg.warnings.forEach(w => {
            results.push({
              parentGroup: sg.target.sourceName,
              accountId: w.accountId,
              groupName: w.sourceName,
              groupId: w.sourceId
            });
          });
        }
      });
      return results;
    };

    let getUniqueGroups = (allGroups) => {
      let result = [];
      allGroups.forEach(g => {
        if (!result.some(g2 => g2.accountId === g.accountId && g2.targetName === g.targetName && g2.region === g.region)) {
          result.push(g);
        }
      });
      return result;
    };

    let getUniqueLoadBalancers = (allLoadBalancers) => {
      let result = [];
      allLoadBalancers.forEach(lb => {
        if (!result.some(lb2 => lb2.targetName === lb.targetName && lb2.accountName === lb.accountName && lb2.region === lb.region)) {
          result.push(lb);
        }
      });
      return result;
    };

    function getKatoTask(task) {
      if (task.getValueFor('kato.tasks')) {
        return task.getValueFor('kato.tasks')[0];
      }
      return null;
    }

    function getEventLog(task) {
      var katoTask = getKatoTask(task);
      if (!katoTask || !katoTask.history) {
        return [];
      }
      return katoTask.history.map(h => h.status);
    }

    return {
      executeMigration: executeMigration,
    };

  });
