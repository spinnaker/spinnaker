'use strict';

angular.module('deckApp.caches.initializer', ['deckApp.subnet.read.service', 'deckApp.loadBalancer.read.service'])
  .factory('cacheInitializer', function(accountService, instanceTypeService, securityGroupService,subnetReader, vpcReader, keyPairsReader, loadBalancerReader) {
    return {
      initialize: function() {
        accountService.getRegionsKeyedByAccount();
        accountService.listAccounts();
        instanceTypeService.getAllTypesByRegion('aws');
        loadBalancerReader.listAWSLoadBalancers();
        securityGroupService.getAllSecurityGroups();
        subnetReader.listSubnets();
        vpcReader.listVpcs();
        keyPairsReader.listKeyPairs();
      }
    };
  });
