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

     it('initializes to default values, setting usePreferredZone flag to true', function () {
       var command = null;
       this.azureServerGroupCommandBuilder.buildNewServerGroupCommand({name: 'appo'}, 'azure').then(function(result) {
         command = result;
       });

       this.$scope.$digest();

       expect(command.viewState.usePreferredZones).toBe(true);
       expect(command.availabilityZones).toEqual(['a', 'b', 'c']);
     });

     it('sets usePreferredZones flag based on initial value', function() {
       spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('custom'));
       var baseServerGroup = {
         account: 'prod',
         region: 'us-west-1',
         asg: {
           availabilityZones: ['g', 'h', 'i'],
           vpczoneIdentifier: '',
         },
       };
       var command = null;

       this.azureServerGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
         command = result;
       });

       this.$scope.$digest();

       expect(command.viewState.usePreferredZones).toBe(true);
       expect(command.availabilityZones).toEqual(['g', 'h', 'i']);

       baseServerGroup.asg.availabilityZones = ['g'];

       this.azureServerGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
         command = result;
       });

       this.$scope.$digest();

       expect(command.viewState.usePreferredZones).toBe(false);
       expect(command.availabilityZones).toEqual(['g']);

     });

     it('sets profile and instance type if available', function() {
       spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('selectedProfile'));

       var baseServerGroup = {
         account: 'prod',
         region: 'us-west-1',
         asg: {
           availabilityZones: ['g', 'h', 'i'],
           vpczoneIdentifier: '',
         },
         launchConfig: {
           instanceType: 'something-custom',
           instanceMonitoring: {},
           securityGroups: [],
         },
       };
       var command = null;

       this.azureServerGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
         command = result;
       });

       this.$scope.$digest();

       expect(command.viewState.instanceProfile).toBe('selectedProfile');
       expect(command.instanceType).toBe('something-custom');
     });
   });

 });
