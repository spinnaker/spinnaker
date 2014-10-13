'use strict';


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
      'task': function(input) {
        return $state.href(
          'home.applications.application.tasks.taskDetails',
          {
            application: input.application,
            taskId: input.taskId
          }
        );
      }
    };

    var pushVersion = /-v\d+$/;

    function asgTask(task, type) {
      var asgName = task.getValueFor(type+'Asg.asgName');
      var account = task.getValueFor('deploy.account.name');
      if (!asgName.match(pushVersion)) { return '/'; }
      return $state.href(
        'home.applications.application.insight.clusters.cluster.serverGroup',
        {
          application: task.getValueFor('application'),
          cluster: asgName.replace(pushVersion, ''),
          account: account,
          accountId: account,
          region: task.getValueFor(type+'Asg.regions')[0],
          serverGroup: asgName,
        });
    }

    function terminateInstancesTask(task) {
      angular.noop(task);
      return '/'; // TODO: where should this go?
    }

    function fromTask(task) {
      var desc = task.getValueFor('description');
      var contains = function(str) {
        return desc.indexOf(str) !== -1;
      };

      switch (true) {
        case contains('Destroying Server Group'):
          return false;
        case contains('Disabling Server Group'):
          return asgTask(task, 'disable');
        case contains('Enabling Server Group'):
          return asgTask(task, 'enable');
        case contains('Resizing Server Group'):
          return asgTask(task, 'resize');
        case contains('Terminating instance'):
          return terminateInstancesTask(task);
        default:
          return false;
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
