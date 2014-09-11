'use strict';

var angular = require('angular');

angular.module('deckApp')
  .factory('urlBuilder', function($state) {
    var lookup = {
      // url for a single serverGroup
      'serverGroups': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters.cluster.serverGroup',
          {
            application: input.application,
            cluster: input.cluster,
            account: input.account,
            accountId: input.account,
            region: input.region,
            serverGroup: input.serverGroup,
          }
        );
      },
      // url for a single instance
      'serverGroupInstances': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters.cluster.instanceDetails',
          {
            application: input.application,
            cluster: input.cluster,
            accountId: input.account,
            instanceId: input.instanceId,
            account: input.account,
          }
        );
      },
      // url for a single cluster
      'clusters': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters.cluster',
          {
            application: input.application,
            cluster: input.cluster,
            account: input.account,
          }
        );
      },
      // url for a single application
      'applications': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters',
          {
            application: input.application,
          }
        );
      },
      // url for a single load balancer
      'loadBalancerServerGroups': function(input) {
        return $state.href(
          'home.applications.application.insight.loadBalancers.loadBalancerDetails',
          {
            application: input.application,
            name: input.loadBalancer,
            region: input.region,
            accountId: input.account
          }
        );
      },
      // url for application tasks
      'tasks': function(input) {
        return $state.href(
          'home.applications.application.tasks',
          {
            application: input.application,
          }
        );
      },
    };

    var pushVersion = /-v\d+$/;

    function getValueForKey(task, k) {
      return task.variables.filter(function(v) {
        return v.key === k;
      })[0];
    }

    function applicationTask(task) {
      return lookup[{
        application: getValueForKey(task, 'application'),
      }];
    }

    function asgTask(task, type) {
      var asgName = getValueForKey(task, type+'disableAsg.asgName');
      var account = getValueForKey(task, 'deploy.account.name');
      if (!asgName.match(pushVersion)) { return '/'; }
      return lookup[{
        application: getValueForKey(task, 'application'),
        cluster: asgName.replace(pushVersion, ''),
        account: account,
        accountId: account,
        region: getValueForKey(task, type+'Asg.regions')[0],
        serverGroup: asgName,
      }];
    }

    function terminateInstancesTask(task) {
      console.log(task);
      return '/'; // TODO: where should this go?
    }

    function fromTask(task) {
      var desc = getValueForKey(task, 'description').indexOf;
      var contains = function(str) {
        return desc.indexOf(str) !== -1;
      };

      switch (true) {
        case contains('Destroying ASG'):
          return applicationTask(task);
        case contains('Disabling ASG'):
          return asgTask(task, 'disable');
        case contains('Enabling ASG'):
          return asgTask(task, 'enable');
        case contains('Resizing ASG'):
          return asgTask(task, 'resize');
        case contains('Terminating instance'):
          return terminateInstancesTask(task);
        default:
          return '/';
      }
    }

    function fromMetadata(input) {
      var builder = lookup[input.type];
      if (angular.isDefined(builder)) {
        return builder(input);
      } else {
        return '/';
      }
    }

    return {
      buildFromTask: fromTask,
      buildFromMetadata: fromMetadata,
    };
  });
