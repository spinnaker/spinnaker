'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.loadBalancer.read.service', [
    require('../caches/infrastructureCaches.js'),
    require('./loadBalancer.transformer.js'),
  ])
  .factory('loadBalancerReader', function ($q, Restangular, searchService, loadBalancerTransformer, infrastructureCaches) {

    function loadLoadBalancers(applicationName) {
      return Restangular
        .one('applications', applicationName)
        .all('loadBalancers').getList().then(function(loadBalancers) {
          loadBalancers.forEach(loadBalancerTransformer.normalizeLoadBalancerWithServerGroups);
          return loadBalancers;
        });
    }


    function getLoadBalancerDetails(provider, account, region, name) {
      return Restangular.one('loadBalancers').one(account).one(region).one(name).get({'provider': provider});
    }

    function listAWSLoadBalancers() {
      return Restangular
        .all('loadBalancers')
        .withHttpConfig({cache: infrastructureCaches.loadBalancers})
        .getList({provider: 'aws'});
    }

    function listGCELoadBalancers() {
      return Restangular
        .all('loadBalancers')
        .withHttpConfig({cache: infrastructureCaches.loadBalancers})
        .getList({provider: 'gce'});
    }

    return {
      loadLoadBalancers: loadLoadBalancers,
      getLoadBalancerDetails: getLoadBalancerDetails,
      listAWSLoadBalancers: listAWSLoadBalancers,
      listGCELoadBalancers: listGCELoadBalancers
    };

  });
