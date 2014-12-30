'use strict';

angular.module('deckApp.caches.initializer', ['deckApp.subnet.read.service'])
  .factory('cacheInitializer', function(accountService, instanceTypeService, oortService, securityGroupService,subnetReader, vpcReader, keyPairsReader) {
    return {
      initialize: function() {
        accountService.getRegionsKeyedByAccount();
        accountService.listAccounts();
        instanceTypeService.getAllTypesByRegion('aws');
        oortService.listAWSLoadBalancers();
        securityGroupService.getAllSecurityGroups();
        subnetReader.listSubnets();
        vpcReader.listVpcs();
        keyPairsReader.listKeyPairs();
      }
    };
  });
