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
])
  .factory('cacheInitializer', function(accountService, instanceTypeService, securityGroupReader, subnetReader, vpcReader, keyPairsReader, loadBalancerReader) {
    return {
      initialize: function() {
        accountService.getRegionsKeyedByAccount();
        accountService.listAccounts();
        instanceTypeService.getAllTypesByRegion('aws');
        loadBalancerReader.listAWSLoadBalancers();
        securityGroupReader.getAllSecurityGroups();
        subnetReader.listSubnets();
        vpcReader.listVpcs();
        keyPairsReader.listKeyPairs();
      }
    };
  });
