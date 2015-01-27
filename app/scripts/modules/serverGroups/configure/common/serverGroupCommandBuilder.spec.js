'use strict';

describe('serverGroupCommandBuilder', function() {
  beforeEach(function() {
    loadDeckWithoutCacheInitializer();
  });

  beforeEach(inject(function(serverGroupCommandBuilder, accountService,$q, $rootScope, subnetReader) {
    this.serverGroupCommandBuilder = serverGroupCommandBuilder;
    this.$scope = $rootScope;
    spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when(AccountServiceFixture.preferredZonesByAccount));
    spyOn(accountService, 'getRegionsKeyedByAccount').and.returnValue($q.when(AccountServiceFixture.regionsKeyedByAccount));
    spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([]));
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
  });

});
