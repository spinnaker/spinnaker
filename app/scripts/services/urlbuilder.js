'use strict';


angular.module('deckApp.urlBuilder', ['ui.router'])
  .factory('urlBuilder', function($state) {
    var lookup = {
      // url for a single serverGroup
      'serverGroups': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters.serverGroup',
          {
            application: input.application,
            cluster: input.cluster,
            account: input.account,
            accountId: input.account,
            region: input.region,
            q: input.serverGroup,
            serverGroup: input.serverGroup,
          },
          { inherit: false }
        );
      },
      // url for a single instance
      'serverGroupInstances': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters.instanceDetails',
          {
            application: input.application,
            cluster: input.cluster,
            accountId: input.account,
            instanceId: input.instanceId,
            account: input.account,
          },
          { inherit: false }
        );
      },
      // url for a single cluster
      'clusters': function(input) {
        console.log(input);
        return $state.href(
          'home.applications.application.insight.clusters',
          {
            application: input.application,
            q: ['cluster:', input.cluster].join(''),
            acct:input.account,
            account: input.account,
          },
          { inherit: false }
        );
      },
      // url for a single application
      'applications': function(input) {
        return $state.href(
          'home.applications.application.insight.clusters',
          {
            application: input.application,
          },
          { inherit: false }
        );
      },
      // url for a single load balancer
      'loadBalancers': function(input) {
        return $state.href(
          'home.applications.application.insight.loadBalancers.loadBalancerDetails',
          {
            application: input.application,
            name: input.loadBalancer,
            region: input.region,
            accountId: input.account,
            vpcId: input.vpcId
          },
          { inherit: false }
        );
      },
      // url for application tasks
      'tasks': function(input) {
        return $state.href(
          'home.applications.application.tasks',
          {
            application: input.application,
          },
          { inherit: false }
        );
      },
      'task': function(input) {
        return $state.href(
          'home.applications.application.tasks.taskDetails',
          {
            application: input.application,
            taskId: input.taskId
          },
          { inherit: false }
        );
      }
    };

    var pushVersion = /-v\d+$/;

    function createCloneTask(task) {
      var regionAndName = task.getValueFor('deploy.server.groups');
      var account = task.getValueFor('deploy.account.name');
      if (!regionAndName) {
        return false;
      }

      var regions = Object.keys(regionAndName),
          region = regions[0],
          asgName = regionAndName[region][0];

      if (!asgName) {
        return false;
      }
      if (!asgName.match(pushVersion)) { return false; }
      return $state.href(
        'home.applications.application.insight.clusters.serverGroup',
        {
          application: asgName.split('-')[0],
          cluster: asgName.replace(pushVersion, ''),
          account: account,
          accountId: account,
          region: regions,
          serverGroup: asgName,
          q: asgName
        });
    }

    function asgTask(task) {
      var asgName = task.getValueFor('asgName');
      var account = task.getValueFor('credentials');
      if (!asgName) {
        return false;
      }
      if (!asgName.match(pushVersion)) { return '/'; }
      return $state.href(
        'home.applications.application.insight.clusters.serverGroup',
        {
          application: asgName.split('-')[0],
          cluster: asgName.replace(pushVersion, ''),
          account: account,
          accountId: account,
          region: task.getValueFor('regions')[0],
          serverGroup: asgName,
        });
    }

    function fromTask(task) {
      var desc = task.name || '';
      var contains = function(str) {
        return desc.indexOf(str) !== -1;
      };

      switch (true) {
        case contains('Destroy Server Group'):
          return false;
        case contains('Disable Server Group'):
          return asgTask(task);
        case contains('Enable Server Group'):
          return asgTask(task);
        case contains('Resize Server Group'):
          return asgTask(task);
        case contains('Create Cloned Server Group'):
          return createCloneTask(task);
        case contains('Create New Server Group'):
          return createCloneTask(task);
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
