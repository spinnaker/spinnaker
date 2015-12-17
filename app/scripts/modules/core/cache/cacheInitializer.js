'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cache.initializer', [
  require('../account/account.service.js'),
  require('../network/network.read.service.js'),
  require('../securityGroup/securityGroup.read.service.js'),
  require('../application/service/applications.read.service.js'),
  require('../ci/jenkins/igor.service.js'),
  require('./infrastructureCaches.js'),
  require('./infrastructureCacheConfig.js'),
  require('../utils/lodash.js'),
  require('../cloudProvider/cloudProvider.registry.js'),
])
  .factory('cacheInitializer', function ($q, applicationReader, infrastructureCaches,
                                         accountService, securityGroupReader, cloudProviderRegistry,
                                         igorService, infrastructureCacheConfig, serviceDelegate, _) {

    var initializers = {
      credentials: [accountService.listAccounts],
      securityGroups: [securityGroupReader.getAllSecurityGroups],
      applications: [applicationReader.listApplications],
      buildMasters: [igorService.listMasters],
    };

    var cacheConfig = _.cloneDeep(infrastructureCacheConfig);

    function setConfigDefaults(key, config) {
      config.version = config.version || 1;
      config.maxAge = config.maxAge || 2 * 24 * 60 * 60 * 1000;
      config.initializers = config.initializers || initializers[key] || [];
      config.onReset = config.onReset || [angular.noop];
    }

    function extendConfig() {
      Object.keys(cacheConfig).forEach((key) => {
        setConfigDefaults(key, cacheConfig[key]);
      });
      accountService.listProviders().then((availableProviders) => {
        cloudProviderRegistry.listRegisteredProviders().forEach((provider) => {
          if (availableProviders.indexOf(provider) < 0) {
            return;
          }
          if (serviceDelegate.hasDelegate(provider, 'cache.configurer')) {
            let providerConfig = serviceDelegate.getDelegate(provider, 'cache.configurer');
            Object.keys(providerConfig).forEach(function(key) {
              setConfigDefaults(key, providerConfig[key]);
              if (!cacheConfig[key]) {
                cacheConfig[key] = providerConfig[key];
              }
              cacheConfig[key].initializers = _.uniq((cacheConfig[key].initializers).concat(providerConfig[key].initializers));
              cacheConfig[key].onReset = _.uniq((cacheConfig[key].onReset).concat(providerConfig[key].onReset));
              cacheConfig[key].version = Math.max(cacheConfig[key].version, providerConfig[key].version);
              cacheConfig[key].maxAge = Math.min(cacheConfig[key].maxAge, providerConfig[key].maxAge);
            });
          }
        });
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
  });
