'use strict';

angular.module('spinnaker.caches.initializer', [
  'spinnaker.subnet.read.service',
  'spinnaker.loadBalancer.read.service',
  'spinnaker.account',
  'spinnaker.account.service',
  'spinnaker.instanceType.service',
  'spinnaker.securityGroup.read.service',
  'spinnaker.subnet.read.service',
  'spinnaker.vpc.read.service',
  'spinnaker.keyPairs.read.service',
  'spinnaker.loadBalancer.read.service',
  'spinnaker.applications.read.service',
  'spinnaker.pipelines.trigger.jenkins.service',
  'spinnaker.caches.infrastructure',
  'spinnaker.caches.infrastructure.config',
])
  .factory('cacheInitializer', function ($q, applicationReader, infrastructureCaches,
                                         accountService, instanceTypeService, securityGroupReader,
                                         subnetReader, vpcReader, keyPairsReader, loadBalancerReader,
                                         igorService, infrastructureCacheConfig) {

    var initializers = {
      credentials: [accountService.getRegionsKeyedByAccount, accountService.listAccounts],
      instanceTypes: [ function() { return instanceTypeService.getAllTypesByRegion('aws'); }],
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
