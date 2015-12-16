'use strict';


let angular = require('angular');

module.exports = angular.module('spinnaker.core.navigation.urlBuilder.service', [
  require('angular-ui-router'),
])
  .factory('urlBuilderService', function($state) {
    var lookup = {
      // url for a single serverGroup
      'serverGroups': function(input) {
        var href = $state.href(
          'home.applications.application.insight.clusters.serverGroup',
          {
            application: input.application,
            accountId: input.account,
            region: input.region,
            serverGroup: input.serverGroup,
            provider: input.provider,
          },
          { inherit: false }
        );
        return buildUrl(href, {q: input.serverGroup, acct: input.account, reg: input.region});
      },
      // url for a single securityGroup
      'securityGroups': function(input) {
        var href = $state.href(
          'home.applications.application.insight.securityGroups.securityGroupDetails',
          {
            application: input.application,
            accountId: input.account,
            region: input.region,
            name: input.name,
            vpcId: input.vpcId,
            provider: input.provider,
          },
          { inherit: false }
        );
        return buildUrl(href, {});
      },
      // url for a single instance
      'instances': function(input) {
        if (!input.application) {
          return $state.href(
            'home.standaloneInstance',
            {
              account: input.account,
              region: input.region,
              instanceId: input.instanceId,
              provider: input.provider,
            }
          );
        }
        var href = $state.href(
          'home.applications.application.insight.clusters.instanceDetails',
          {
            application: input.application,
            instanceId: input.instanceId,
            provider: input.provider,
          },
          { inherit: false }
        );
        return buildUrl(href, {q: input.serverGroup, acct: input.account, reg: input.region});
      },
      // url for a single cluster
      'clusters': function(input) {
        let filters = {
          acct: input.account,
        };
        if (input.cluster) {
          filters.q = ['cluster:', input.cluster].join('');
        }
        if (input.stack) {
          filters.stack = input.stack;
        }
        if (input.detail) {
          filters.q = ['detail:', input.detail].join('');
        }
        if (input.region) {
          filters.reg = input.region;
        }
        var href = $state.href(
          'home.applications.application.insight.clusters',
          {
            application: input.application,
          },
          { inherit: false }
        );
        if (input.project) {
          href = $state.href(
            'home.project.application.insight.clusters',
            {
              application: input.application,
              project: input.project,
            },
            { inherit: false }
          );
        }
        return buildUrl(href, filters);
      },
      // url for a single application
      'applications': function(input) {
        if (input.project) {
          return $state.href(
            'home.project.application.insight.clusters',
            { application: input.application, project: input.project },
            { inherit: false }
          );
        }
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
        var href = $state.href(
          'home.applications.application.insight.loadBalancers.loadBalancerDetails',
          {
            application: input.application,
            name: input.loadBalancer,
            region: input.region,
            accountId: input.account,
            vpcId: input.vpcId,
            provider: input.provider,
          },
          { inherit: false }
        );
        return buildUrl(href, {q: input.loadBalancer, reg: input.region, acct: input.account });
      },
      'projects': function(input) {
        return $state.href('home.project.dashboard', { project: input.name }, { inherit: false });
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
      if (!regionAndName || !Object.keys(regionAndName)[0]) {
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

    /**
     * Internals copied from Angular source
     */
    function forEachSorted(obj, iterator, context) {
      var keys = sortedKeys(obj);
      for (var i = 0; i < keys.length; i++) {
        iterator.call(context, obj[keys[i]], keys[i]);
      }
      return keys;
    }

    function sortedKeys(obj) {
      var keys = [];
      for (var key in obj) {
        if (obj.hasOwnProperty(key)) {
          keys.push(key);
        }
      }
      return keys.sort();
    }

    function buildUrl(url, params) {
      if (!params) {
        return url;
      }
      var parts = [];
      forEachSorted(params, function(value, key) {
        if (value === null || angular.isUndefined(value)) {
          return;
        }
        if (!angular.isArray(value)) {
          value = [value];
        }

        angular.forEach(value, function(v) {
          if (angular.isObject(v)) {
            if (angular.isDate(v)){
              v = v.toISOString();
            } else {
              v = angular.toJson(v);
            }
          }
          parts.push(encodeUriQuery(key) + '=' +
            encodeUriQuery(v));
        });
      });
      if(parts.length > 0) {
        url += ((url.indexOf('?') === -1) ? '?' : '&') + parts.join('&');
      }
      return url;
    }

    function encodeUriQuery(val, pctEncodeSpaces) {
      return encodeURIComponent(val).
        replace(/%40/gi, '@').
        replace(/%3A/gi, ':').
        replace(/%24/g, '$').
        replace(/%2C/gi, ',').
        replace(/%20/g, (pctEncodeSpaces ? '%20' : '+'));
    }

    return {
      buildFromTask: fromTask,
      buildFromMetadata: fromMetadata,
    };
  });
