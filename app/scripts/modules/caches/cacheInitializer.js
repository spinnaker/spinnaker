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
  'deckApp.caches.infrastructure',
])
  .factory('cacheInitializer', function ($q, applicationReader, infrastructureCaches, accountService, instanceTypeService, securityGroupReader, subnetReader, vpcReader, keyPairsReader, loadBalancerReader) {

    function initialize() {
      return $q.all([
        accountService.getRegionsKeyedByAccount(),
        accountService.listAccounts(),
        instanceTypeService.getAllTypesByRegion('aws'),
        loadBalancerReader.listAWSLoadBalancers(),
        securityGroupReader.getAllSecurityGroups(),
        subnetReader.listSubnets(),
        vpcReader.listVpcs(),
        keyPairsReader.listKeyPairs(),
        applicationReader.listApplications(),
      ]);
    }

    function refreshCaches() {
      infrastructureCaches.clearCaches();
      return initialize();
    }

    return {
      initialize: initialize,
      refreshCaches: refreshCaches,
    };
  });
