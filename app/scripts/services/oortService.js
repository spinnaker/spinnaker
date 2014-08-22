'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('oortService', function ($http, settings, $q, Restangular, _) {

    var oortEndpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);
    });

    function listApplications() {
      return oortEndpoint.all('applications').getList();
    }

    function getApplicationEndpoint(application) {
      return oortEndpoint.one('applications', application);
    }

    function getApplication(application) {
      var retrievedApplication = getApplicationEndpoint(application).get();
      return retrievedApplication.then(function(retrieved) {
        var clusters = retrieved.clusters;
        retrieved.accounts = Object.keys(clusters);
        var clusterLoader = loadClusters(retrieved, clusters).then(function(hydratedClusters) {
          retrieved.clusters = hydratedClusters;
        });
        var loadBalancerLoader = loadLoadBalancers(retrieved, clusters).then(function(hydratedLoadBalancers) {
          retrieved.loadBalancers = hydratedLoadBalancers;
        });

        retrieved.getCluster = getClusterFromApplicationBuilder(retrieved);
        return $q.all([clusterLoader, loadBalancerLoader]).then(function() {
          normalizeLoadBalancersAndServerGroups(retrieved);
          return retrieved;
        });
      });
    }

    function getClusterFromApplicationBuilder(application) {
      return function(accountName, clusterName) {
        var matches = application.clusters.filter(function (cluster) {
          return cluster.name === clusterName && cluster.account === accountName;
        });
        return matches.length ? matches[0] : null;
      };
    }

    function loadClusters(application, clusters) {
      var clusterPromises = [];

      if (application.clusterLoader) {
        return application.clusterLoader;
      }

      application.accounts.forEach(function (account) {
        var accountClusters = clusters[account];
        accountClusters.forEach(function (cluster) {
          var clusterPromise = getCluster(application.name, account, cluster.name);
          clusterPromises.push(clusterPromise);
        });
      });

      application.clusterLoader = $q.all(clusterPromises);
      return application.clusterLoader;
    }

    function loadLoadBalancers(application, clusters) {
      var loadBalancerNames = [],
          loadBalancerPromises = [];

      if (application.loadBalancerLoader) {
        return application.loadBalancerLoader;
      }

      application.accounts.forEach(function(account) {
        var accountClusters = clusters[account];
        accountClusters.forEach(function(cluster) {
          loadBalancerNames.push(cluster.loadBalancers);
        });
      });

      loadBalancerNames = _.unique(_.flatten(loadBalancerNames));

      loadBalancerNames.forEach(function(loadBalancer) {
        var loadBalancerPromise = getLoadBalancer(loadBalancer, application);
        loadBalancerPromises.push(loadBalancerPromise);
      });

      var loader = $q.all(loadBalancerPromises);
      application.loadBalancerLoader = loader.then(_.flatten);
      return application.loadBalancerLoader;

    }

    function serverGroupIsInLoadBalancer(serverGroup, loadBalancer, serverGroupNames) {
      if (serverGroup.region !== loadBalancer.region || serverGroupNames.indexOf(serverGroup.name) === -1) {
        return false;
      }
      // only include if load balancer is fronting an instance
      var elbInstanceIds = _.pluck(loadBalancer.elb.instances, 'instanceId'),
        serverGroupInstanceIds = _.pluck(serverGroup.instances, 'instanceId');
      return elbInstanceIds.some(function (test) {
        return serverGroupInstanceIds.indexOf(test) !== -1;
      });
    }

    function normalizeLoadBalancersAndServerGroups(application) {
      application.loadBalancers.forEach(function(loadBalancer) {
        addServerGroupsToLoadBalancer(loadBalancer, application.clusters);
        loadBalancer.instances = _.flatten(_.collect(loadBalancer.serverGroups, 'instances'));
        addHealthCountsToLoadBalancer(loadBalancer);
      });
    }

    function addServerGroupsToLoadBalancer(loadBalancer, clusters) {
      var serverGroupNames = loadBalancer.serverGroups;
      loadBalancer.serverGroups = [];
      var clusterMatches = clusters.filter(function (cluster) {
        return cluster.account === loadBalancer.account;
      });
      clusterMatches.forEach(function (matchedCluster) {
        matchedCluster.serverGroups.forEach(function (serverGroup) {
          if (serverGroupIsInLoadBalancer(serverGroup, loadBalancer, serverGroupNames)) {
            serverGroup.loadBalancers = serverGroup.loadBalancers || [];
            serverGroup.loadBalancers.push(loadBalancer);
            loadBalancer.serverGroups.push(serverGroup);
          }
        });
      });
      loadBalancer.serverGroupNames = _.pluck(loadBalancer.serverGroups, 'name');
    }

    function addHealthCountsToLoadBalancer(loadBalancer) {
      loadBalancer.health = {
        upCount: loadBalancer.instances.filter(function (instance) {
          return instance.healthStatus === 'Healthy';
        }).length,
        downCount: loadBalancer.instances.filter(function (instance) {
          return instance.healthStatus === 'Unhealthy';
        }).length,
        unknownCount: loadBalancer.instances.filter(function (instance) {
          return instance.healthStatus === 'Unknown';
        }).length
      };
    }

    function getLoadBalancer(name) {
      var promise = oortEndpoint.one('aws').one('loadBalancers', name).get();
      return promise.then(function(loadBalancerRollup) {
        var loadBalancers = [];
        loadBalancerRollup.accounts.forEach(function (account) {
          account.regions.forEach(function (region) {
            region.loadBalancers.forEach(function (loadBalancer) {
              loadBalancer.account = account.name;
              loadBalancers.push(loadBalancer);
            });
          });
        });
        return loadBalancers;
      });
    }

    function getClustersForAccountEndpoint(application, account) {
      return getApplicationEndpoint(application).all('clusters').all(account);
    }

    function getCluster(application, account, clusterName) {
      return getClustersForAccountEndpoint(application, account).all(clusterName).getList().then(function(cluster) {
        if (!cluster.length) {
          console.error('NO SERVER GROUPS', cluster); // TODO: remove when https://github.com/spinnaker/oort/issues/35 resolved
          return {
            account: account,
            serverGroups: []
          };
        }
        cluster[0].serverGroups.forEach(function(serverGroup) {
          normalizeServerGroup(serverGroup, account, clusterName);
        });
        cluster[0].account = account;
        addHealthCountsToCluster(cluster[0]);
        return cluster[0];
      });
    }

    function addHealthCountsToCluster(cluster) {
      cluster.upCount = 0;
      cluster.downCount = 0;
      cluster.unknownCount = 0;
      if (!cluster.serverGroups) {
        return;
      }
      cluster.serverGroups.forEach(function(serverGroup) {
        cluster.upCount += serverGroup.upCount;
        cluster.downCount += serverGroup.downCount;
        cluster.unknownCount += serverGroup.unknownCount;
      });
    }

    function addInstancesOnlyFoundInAsg(serverGroup) {
      var foundIds = serverGroup.instances.map(function (instance) {
        return instance.instanceId;
      });
      var rejected = serverGroup.asg.instances.filter(function (asgInstance) {
        return foundIds.indexOf(asgInstance.instanceId) === -1;
      });
      rejected.forEach(function(rejected) {
        rejected.serverGroup = serverGroup.name;
      });
      serverGroup.instances = serverGroup.instances.concat(rejected);
    }

    function addHealthyCounts(serverGroup) {
      serverGroup.upCount = _.filter(serverGroup.instances, {healthStatus: 'Healthy'}).length;
      serverGroup.downCount = _.filter(serverGroup.instances, {healthStatus: 'Unhealthy'}).length;
      serverGroup.unknownCount = _.filter(serverGroup.instances, {healthStatus: 'Unknown'}).length;
    }

    function extendInstancesWithAsgInstances(serverGroup) {
      if (serverGroup.instances && serverGroup.instances.length) {
        serverGroup.instances.forEach(function (instance) {
          var asgInstance = serverGroup.asg.instances.filter(function (asgInstance) {
            return asgInstance.instanceId === instance.instanceId;
          })[0];
          angular.extend(instance, asgInstance);
        });
      }
    }

    function normalizeServerGroup(serverGroup, accountName, clusterName) {
      var suspendedProcesses = _.collect(serverGroup.asg.suspendedProcesses, 'processName'),
          disabledProcessFlags = ['AddToLoadBalancer', 'Launch', 'Terminate'];
      serverGroup.instances = serverGroup.instances.map(function(instance) {
        var toReturn = instance.instance;
        toReturn.account = accountName;
        return toReturn;
      });
      serverGroup.account = accountName;
      serverGroup.cluster = clusterName;
      serverGroup.isDisabled = _.intersection(disabledProcessFlags, suspendedProcesses).length === disabledProcessFlags.length;
      extendInstancesWithAsgInstances(serverGroup);
      addInstancesOnlyFoundInAsg(serverGroup);
      addHealthyCounts(serverGroup);
    }

    return {
      listApplications: listApplications,
      getApplication: getApplication
    };
  });
