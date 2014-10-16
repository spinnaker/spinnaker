'use strict';

describe('scheduledCache', function() {
  beforeEach(function() {
    module('deckApp');
  });

  beforeEach(inject(function(serverGroupService) {
    this.serverGroupService = serverGroupService;
  }));

  it('parses server group name', function() {
    expect(this.serverGroupService.parseServerGroupName('app-v000'))
      .toEqual({application: 'app', stack: '', freeFormDetails: ''});

    expect(this.serverGroupService.parseServerGroupName('app-test-v000'))
      .toEqual({application: 'app', stack: 'test', freeFormDetails: ''});

    expect(this.serverGroupService.parseServerGroupName('app-test-detail-v000'))
      .toEqual({application: 'app', stack: 'test', freeFormDetails: 'detail'});

    expect(this.serverGroupService.parseServerGroupName('app--detail-v000'))
      .toEqual({application: 'app', stack: '', freeFormDetails: 'detail'});

  });

  it('creates cluster name', function() {

    expect(this.serverGroupService.getClusterName('app', 'cluster', 'details')).toBe('app-cluster-details');
    expect(this.serverGroupService.getClusterName('app', null, 'details')).toBe('app--details');
    expect(this.serverGroupService.getClusterName('app', null, null)).toBe('app');
    expect(this.serverGroupService.getClusterName('app', 'cluster', null)).toBe('app-cluster');


  });
});
