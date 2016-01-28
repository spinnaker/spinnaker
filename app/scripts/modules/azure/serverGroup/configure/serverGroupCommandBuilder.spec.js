 'use strict';

 xdescribe('azureServerGroupCommandBuilder', function() {
   const AccountServiceFixture = require('../../../../../../test/fixture/AccountServiceFixtures');

   beforeEach(
     window.module(
       require('./serverGroupCommandBuilder.service.js')
     )
   );

   beforeEach(window.inject(function(azureServerGroupCommandBuilder, accountService, diffService, $q, $rootScope, subnetReader, instanceTypeService) {
     this.azureServerGroupCommandBuilder = azureServerGroupCommandBuilder;
     this.$scope = $rootScope;
     this.instanceTypeService = instanceTypeService;
     this.$q = $q;
     spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when(AccountServiceFixture.preferredZonesByAccount));
     spyOn(accountService, 'getRegionsKeyedByAccount').and.returnValue($q.when(AccountServiceFixture.regionsKeyedByAccount));
     spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([]));
     spyOn(accountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(
       this.$q.when(['a', 'b', 'c'])
     );
     spyOn(diffService, 'getClusterDiffForAccount').and.returnValue(
         this.$q.when({})
     );
   }));

   describe('create server group commands', function() {

     it('initializes to default values.', function () {
       var command = null;
       this.azureServerGroupCommandBuilder.buildNewServerGroupCommand({name: 'appo'}, {account: 'azure-cred1', region: 'westus'}).then(function(result) {
         command = result;
       });

       this.$scope.$digest();

       expect(command.application).toEqual('appo');
       expect(command.credentials).toEqual('azure-cred1');
       expect(command.region).toEqual('westus');
       expect(command.strategy).toEqual('');
       expect(command.capacity.min).toEqual(1);
       expect(command.capacity.max).toEqual(1);
       expect(command.capacity.desired).toEqual(1);
       expect(command.selectedProvider).toEqual('azure');
       expect(command.securityGroups).toEqual([]);
       expect(command.viewState.instanceProfile).toEqual('custom');
       expect(command.viewState.allImageSelection).toBe(null);
       expect(command.viewState.useAllImageSelection).toBe(false);
       expect(command.viewState.useSimpleCapacity).toBe(true);
       expect(command.viewState.usePrefereedZones).toBe(true);
       expect(command.viewState.mode).toEqual('create');
       expect(command.viewState.disableStrategySelection).toBe(true);
     });
   });

 });
