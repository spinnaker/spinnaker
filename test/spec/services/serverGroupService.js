'use strict';

describe('serverGroupService', function() {
  beforeEach(function() {
    loadDeckWithoutCacheInitializer();
  });

  beforeEach(inject(function(serverGroupService, accountService,$q, $rootScope, subnetReader) {
    this.serverGroupService = serverGroupService;
    this.$scope = $rootScope;
    spyOn(accountService, 'getPreferredZonesByAccount').and.returnValue($q.when(AccountServiceFixture.preferredZonesByAccount));
    spyOn(accountService, 'getRegionsKeyedByAccount').and.returnValue($q.when(AccountServiceFixture.regionsKeyedByAccount));
    spyOn(subnetReader, 'listSubnets').and.returnValue($q.when([]));
  }));

  describe('parseServerGroupName', function() {
    it('parses server group name with no stack or details', function() {
      expect(this.serverGroupService.parseServerGroupName('app-v001'))
        .toEqual({application: 'app', stack: '', freeFormDetails: ''});
      expect(this.serverGroupService.parseServerGroupName('app-test-v001'))
        .toEqual({application: 'app', stack: 'test', freeFormDetails: ''});
      expect(this.serverGroupService.parseServerGroupName('app--detail-v001'))
        .toEqual({application: 'app', stack: '', freeFormDetails: 'detail'});
      expect(this.serverGroupService.parseServerGroupName('app--detail-withdashes-v001'))
        .toEqual({application: 'app', stack: '', freeFormDetails: 'detail-withdashes'});
    });

    it('parses server group name with no version', function() {
      expect(this.serverGroupService.parseServerGroupName('app'))
        .toEqual({application: 'app', stack: '', freeFormDetails: ''});
      expect(this.serverGroupService.parseServerGroupName('app-test'))
        .toEqual({application: 'app', stack: 'test', freeFormDetails: ''});
      expect(this.serverGroupService.parseServerGroupName('app--detail'))
        .toEqual({application: 'app', stack: '', freeFormDetails: 'detail'});
      expect(this.serverGroupService.parseServerGroupName('app--detail-withdashes'))
        .toEqual({application: 'app', stack: '', freeFormDetails: 'detail-withdashes'});
    });

  });

  describe('create server group commands', function() {

    it('initializes to default values, setting usePreferredZone flag to true', function () {
      var command = null;
      this.serverGroupService.buildNewServerGroupCommand({name: 'appo'}, 'aws').then(function(result) {
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

      this.serverGroupService.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(true);
      expect(command.availabilityZones).toEqual(['g', 'h', 'i']);

      baseServerGroup.asg.availabilityZones = ['g'];

      this.serverGroupService.buildServerGroupCommandFromExisting({name: 'appo'}, baseServerGroup).then(function(result) {
        command = result;
      });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(false);
      expect(command.availabilityZones).toEqual(['g']);

    });
  });

  it('creates cluster name', function() {
    expect(this.serverGroupService.getClusterName('app', null, null)).toBe('app');
    expect(this.serverGroupService.getClusterName('app', 'cluster', null)).toBe('app-cluster');
    expect(this.serverGroupService.getClusterName('app', null, 'details')).toBe('app--details');
    expect(this.serverGroupService.getClusterName('app', null, 'details-withdash')).toBe('app--details-withdash');
    expect(this.serverGroupService.getClusterName('app', 'cluster', 'details')).toBe('app-cluster-details');
    expect(this.serverGroupService.getClusterName('app', 'cluster', 'details-withdash')).toBe('app-cluster-details-withdash');

  });
});
