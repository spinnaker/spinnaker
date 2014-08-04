'use strict';

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
      retrievedApplication.then(function(retrieved) {
        var clusters = retrieved.clusters;
        retrieved.accounts = Object.keys(clusters);
        retrieved.getClusters = loadClusters.bind(null, retrieved, clusters);
        retrieved.getLoadBalancers = loadLoadBalancers.bind(null, retrieved, clusters);
        delete retrieved.clusters;
      });
      return retrievedApplication;
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
        var loadBalancerPromise = getLoadBalancer(loadBalancer);
        loadBalancerPromises.push(loadBalancerPromise);
      });

      var loader = $q.all(loadBalancerPromises);
      application.loadBalancerLoader = loader.then(_.flatten);
      return application.loadBalancerLoader;

    }

    function getLoadBalancers(application, account, cluster) {
      return getClustersForAccount(application, account).all(cluster).one('aws').one('loadBalancers').getList();
    }

    function getLoadBalancer(name) {
      return oortEndpoint.one('aws').one('loadBalancers', name).get().then(function(loadBalancerRollup) {
        var loadBalancers = [];
        loadBalancerRollup.accounts.forEach(function(account) {
          var accountName = account.name;
          account.regions.forEach(function(region) {
            region.loadBalancers.forEach(function(loadBalancer) {
              loadBalancer.account = accountName;
              loadBalancer.serverGroupNames = loadBalancer.serverGroups;
              delete loadBalancer.serverGroups;
              loadBalancers.push(loadBalancer);
            });
          });
        });
        return loadBalancers;
      });
    }

    function getClusters(application) {
      return getApplicationEndpoint(application).all('clusters');
    }

    function getClustersForAccount(application, account) {
      return getApplicationEndpoint(application).all('clusters').all(account);
    }

    function getCluster(application, account, clusterName) {
      return getClustersForAccount(application, account).all(clusterName).getList().then(function(cluster) {
        cluster[0].serverGroups.forEach(function(serverGroup) {
          transformServerGroup(serverGroup, account, clusterName);
        });
        cluster[0].getLoadBalancers = getLoadBalancers.bind(null, application, account, clusterName);
        cluster[0].account = account;
        return cluster[0];
      });
    }

    function getServerGroup(application, account, cluster, serverGroup) {
      return getClustersForAccount(application, account).one('aws').one('serverGroups', serverGroup).get().then(
        function(serverGroup) {
          transformServerGroup(serverGroup, account, cluster);
        });
    }

    function transformServerGroup(serverGroup, account, cluster) {
      serverGroup.account = account;
      serverGroup.cluster = cluster;
      serverGroup.upCount = _.filter(serverGroup.instances, {isHealthy: true}).length;
    }

    function getInstance(application, account, cluster, serverGroup, instance) {
      return getServerGroup(application, account, cluster, serverGroup)
        .then(function(serverGroup) {
          var matches = serverGroup.instances.filter(function(retrievedInstance) { return retrievedInstance.name === instance; });
          return matches && matches.length ? matches[0] : null;
        });
    }

    return {
      listApplications: listApplications,
      getApplication: getApplication,
      getClusters: getClusters,
      getLoadBalancers: getLoadBalancers,
      getClustersForAccount: getClustersForAccount,
      getCluster: getCluster,
      getServerGroup: getServerGroup,
      getInstance: getInstance
    };
  });
