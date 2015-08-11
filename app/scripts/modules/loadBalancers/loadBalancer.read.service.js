'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.loadBalancer.read.service', [
    require('../vpc/vpc.read.service.js'),
    require('../caches/infrastructureCaches.js'),
    require('./loadBalancer.transformer.js'),
  ])
  .factory('loadBalancerReader', function ($q, Restangular, searchService, loadBalancerTransformer, infrastructureCaches, vpcReader) {

    function loadLoadBalancers(applicationName) {
      var vpcLoader = vpcReader.listVpcs();
      var loadBalancerLoader = Restangular.one('applications', applicationName).all('loadBalancers').getList();
        return $q.all({vpcs: vpcLoader, loadBalancers: loadBalancerLoader}).then(function(results) {
          results.loadBalancers.forEach(loadBalancerTransformer.normalizeLoadBalancerWithServerGroups);
          results.loadBalancers.forEach(addVpcNameToLoadBalancer(results.vpcs));
          return results.loadBalancers;
        });
    }

    function addVpcNameToLoadBalancer(vpcs) {
      return function(loadBalancer) {
        var matches = vpcs.filter(function(test) {
          return test.id === loadBalancer.vpcId;
        });
        loadBalancer.vpcName = matches.length ? matches[0].name : '';
      };
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

  })
  .name;
