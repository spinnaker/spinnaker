'use strict';

angular.module('deckApp.caches.initializer', [
  'deckApp.subnet.read.service',
  'deckApp.loadBalancer.read.service',
  'deckApp.account',
  'deckApp.account.service',
  'deckApp.instanceType.service',
  'deckApp.securityGroup.read.service',
  'deckApp.subnet.read.service',
  'deckApp.vpc.read.service',
  'deckApp.keyPairs.read.service',
  'deckApp.loadBalancer.read.service',
  'deckApp.applications.read.service',
  'deckApp.pipelines.trigger.jenkins.service',
  'deckApp.caches.infrastructure',
  'deckApp.caches.infrastructure.config',
])
  .factory('cacheInitializer', function ($q, applicationReader, infrastructureCaches,
                                         accountService, instanceTypeService, securityGroupReader,
                                         subnetReader, vpcReader, keyPairsReader, loadBalancerReader,
                                         igorService, infrastructureCacheConfig) {

    var initializers = {
      credentials: [accountService.getRegionsKeyedByAccount, accountService.listAccounts],
      instanceTypes: [ function() { instanceTypeService.getAllTypesByRegion('aws'); }],
      loadBalancers: [loadBalancerReader.listAWSLoadBalancers],
      securityGroups: [securityGroupReader.getAllSecurityGroups],
      subnets: [subnetReader.listSubnets],
      vpcs: [vpcReader.listVpcs],
      keyPairs: [keyPairsReader.listKeyPairs],
      applications: [applicationReader.listApplications],
      buildMasters: [igorService.listMasters],
    };

    function initialize() {
      var all = [];
      Object.keys(infrastructureCacheConfig).forEach(function(key) {
        all.push(initializeCache(key));
      });
      return $q.all(all);
    }

    function initializeCache(key) {
      infrastructureCaches.createCache(key, infrastructureCacheConfig[key]);
      if (initializers[key]) {
        var initializer = initializers[key];
        var all = [];
        initializer.forEach(function(method) {
          all.push(method());
        });
        return $q.all(all);
      }
    }

    function refreshCache(key) {
      infrastructureCaches.clearCache(key);
      return initializeCache(key);
    }

    function refreshCaches() {
      var all = [];
      Object.keys(initializers).forEach(function(key) {
        all.push(refreshCache(key));
      });
      return $q.all(all);
    }

    return {
      initialize: initialize,
      refreshCaches: refreshCaches,
      refreshCache: refreshCache,
    };
  });
