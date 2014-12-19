'use strict';

angular.module('deckApp')
  .factory('cacheInitializer', function(accountService, instanceTypeService, oortService, securityGroupService, mortService) {
    return {
      initialize: function() {
        accountService.getRegionsKeyedByAccount();
        accountService.listAccounts();
        instanceTypeService.getAllTypesByRegion('aws');
        oortService.listAWSLoadBalancers();
        securityGroupService.getAllSecurityGroups();
        mortService.listSubnets();
        mortService.listVpcs();
        mortService.listKeyPairs();
      }
    };
  });
