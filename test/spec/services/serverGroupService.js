'use strict';

describe('serverGroupService', function() {
  beforeEach(function() {
    module('deckApp');
  });

  beforeEach(inject(function(serverGroupService) {
    this.serverGroupService = serverGroupService;
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

  it('creates cluster name', function() {
    expect(this.serverGroupService.getClusterName('app', null, null)).toBe('app');
    expect(this.serverGroupService.getClusterName('app', 'cluster', null)).toBe('app-cluster');
    expect(this.serverGroupService.getClusterName('app', null, 'details')).toBe('app--details');
    expect(this.serverGroupService.getClusterName('app', null, 'details-withdash')).toBe('app--details-withdash');
    expect(this.serverGroupService.getClusterName('app', 'cluster', 'details')).toBe('app-cluster-details');
    expect(this.serverGroupService.getClusterName('app', 'cluster', 'details-withdash')).toBe('app-cluster-details-withdash');

  });
});
