'use strict';

describe('serverGroupCommandBuilder', function() {
  beforeEach(function() {
    loadDeckWithoutCacheInitializer();
  });

  beforeEach(inject(function(serverGroupCommandBuilder, accountService,$q, $rootScope, subnetReader, instanceTypeService) {
    this.serverGroupCommandBuilder = serverGroupCommandBuilder;
    this.$scope = $rootScope;
    this.instanceTypeService = instanceTypeService;
    this.$q = $q;
    spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when(AccountServiceFixture.preferredZonesByAccount));
    spyOn(accountService, 'getRegionsKeyedByAccount').and.returnValue($q.when(AccountServiceFixture.regionsKeyedByAccount));
    spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([]));
    spyOn(accountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(
      this.$q.when(['a', 'b', 'c'])
    );
  }));

  describe('create server group commands', function() {

    it('initializes to default values, setting usePreferredZone flag to true', function () {
      var command = null;
      this.serverGroupCommandBuilder.buildNewServerGroupCommand({name: 'appo'}, 'aws').then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(true);
      expect(command.availabilityZones).toEqual(['a', 'b', 'c']);
    });

    it('sets usePreferredZones flag based on initial value', function() {

      var baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        asg: {
          availabilityZones: ['g', 'h', 'i'],
          vpczoneIdentifier: '',
        },
      };
      var command = null;

      this.serverGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(true);
      expect(command.availabilityZones).toEqual(['g', 'h', 'i']);

      baseServerGroup.asg.availabilityZones = ['g'];

      this.serverGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
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

      this.serverGroupCommandBuilder.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.instanceProfile).toBe('selectedProfile');
      expect(command.instanceType).toBe('something-custom');
    });
  });

});
