'use strict';

angular.module('deckApp')
  .factory('infrastructureCaches', function($cacheFactory) {

    var caches = {};

    function addCache(cacheId) {
      $cacheFactory(cacheId);
      caches[cacheId] = $cacheFactory.get(cacheId);
    }

    addCache('credentials');
    addCache('vpcs');
    addCache('subnets');
    addCache('loadBalancers');
    addCache('securityGroups');

    return caches;
  });
