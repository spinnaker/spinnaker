'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.caches.initializer', [
  require('../subnet/subnet.read.service.js'),
  require('../loadBalancers/loadBalancer.read.service.js'),
  //require('../account/account.module.js'),
  require('../account/accountService.js'),
  require('../../services/instanceTypeService.js'),
  require('../securityGroups/securityGroup.read.service.js'),
  require('../subnet/subnet.read.service.js'),
  require('../vpc/vpc.read.service.js'),
  require('../keyPairs/keyParis.read.service.js'),
  require('../loadBalancers/loadBalancer.read.service.js'),
  require('../applications/applications.read.service.js'),
  require('../jenkins/index.js'),
  require('./infrastructureCaches.js'),
  require('./infrastructureCacheConfig.js'),
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
  })
  .name;
