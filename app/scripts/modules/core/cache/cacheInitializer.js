'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cache.initializer', [
  require('../../account/account.service.js'),
  require('../../network/network.read.service.js'),
  require('../../securityGroups/securityGroup.read.service.js'),
  require('../../applications/applications.read.service.js'),
  require('../../jenkins/igor.service.js'),
  require('./infrastructureCaches.js'),
  require('./infrastructureCacheConfig.js'),
  require('../../utils/lodash.js'),
  require('../cloudProvider/cloudProvider.registry.js'),
])
  .factory('cacheInitializer', function ($q, applicationReader, infrastructureCaches,
                                         accountService, securityGroupReader, cloudProviderRegistry,
                                         igorService, infrastructureCacheConfig, serviceDelegate, networkReader, _) {

    var initializers = {
      credentials: [accountService.listAccounts],
      networks: [networkReader.listNetworks],
      securityGroups: [securityGroupReader.getAllSecurityGroups],
      applications: [applicationReader.listApplications],
      buildMasters: [igorService.listMasters],
    };

    var cacheConfig = _.cloneDeep(infrastructureCacheConfig);

    function setConfigDefaults(key, config) {
      config.version = config.version || 1;
      config.maxAge = config.maxAge || 2 * 24 * 60 * 60 * 1000;
      config.initializers = config.initializers || initializers[key] || [];
    }

    function extendConfig() {
      Object.keys(cacheConfig).forEach((key) => {
        setConfigDefaults(key, cacheConfig[key]);
      });
      cloudProviderRegistry.listRegisteredProviders().forEach((provider) => {
        let providerConfig = serviceDelegate.getDelegate(provider, 'cache.configurer');
        if (providerConfig) {
          Object.keys(providerConfig).forEach(function(key) {
            setConfigDefaults(key, providerConfig[key]);
            if (!cacheConfig[key]) {
              cacheConfig[key] = providerConfig[key];
            }
            cacheConfig[key].initializers = _.uniq((cacheConfig[key].initializers).concat(providerConfig[key].initializers));
            cacheConfig[key].version = Math.max(cacheConfig[key].version, providerConfig[key].version);
            cacheConfig[key].maxAge = Math.min(cacheConfig[key].maxAge, providerConfig[key].maxAge);

          });
        }
      });
    }

    function initialize() {
      extendConfig();
      var all = [];
      Object.keys(cacheConfig).forEach(function(key) {
        all.push(initializeCache(key));
      });
      return $q.all(all);
    }

    function initializeCache(key) {
      infrastructureCaches.createCache(key, cacheConfig[key]);
      if (cacheConfig[key].initializers) {
        var initializer = cacheConfig[key].initializers;
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
      Object.keys(cacheConfig).forEach(function(key) {
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
