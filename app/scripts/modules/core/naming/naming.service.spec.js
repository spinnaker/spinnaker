'use strict';

describe('namingService', function() {
  beforeEach(function() {
    window.module(
      require('./naming.service')
    );
  });

  beforeEach(window.inject(function(namingService) {
    this.namingService = namingService;
  }));

  describe('parseServerGroupName', function() {
    it('parses server group name with no stack or details', function() {
      expect(this.namingService.parseServerGroupName('app-v001'))
        .toEqual({application: 'app', stack: '', freeFormDetails: ''});
      expect(this.namingService.parseServerGroupName('app-test-v001'))
        .toEqual({application: 'app', stack: 'test', freeFormDetails: ''});
      expect(this.namingService.parseServerGroupName('app--detail-v001'))
        .toEqual({application: 'app', stack: '', freeFormDetails: 'detail'});
      expect(this.namingService.parseServerGroupName('app--detail-withdashes-v001'))
        .toEqual({application: 'app', stack: '', freeFormDetails: 'detail-withdashes'});
    });

    it('parses server group name with no version', function() {
      expect(this.namingService.parseServerGroupName('app'))
        .toEqual({application: 'app', stack: '', freeFormDetails: ''});
      expect(this.namingService.parseServerGroupName('app-test'))
        .toEqual({application: 'app', stack: 'test', freeFormDetails: ''});
      expect(this.namingService.parseServerGroupName('app--detail'))
        .toEqual({application: 'app', stack: '', freeFormDetails: 'detail'});
      expect(this.namingService.parseServerGroupName('app--detail-withdashes'))
        .toEqual({application: 'app', stack: '', freeFormDetails: 'detail-withdashes'});
    });

  });

  it('returns cluster name', function() {
    expect(this.namingService.getClusterName('app', null, null)).toBe('app');
    expect(this.namingService.getClusterName('app', 'cluster', null)).toBe('app-cluster');
    expect(this.namingService.getClusterName('app', null, 'details')).toBe('app--details');
    expect(this.namingService.getClusterName('app', null, 'details-withdash')).toBe('app--details-withdash');
    expect(this.namingService.getClusterName('app', 'cluster', 'details')).toBe('app-cluster-details');
    expect(this.namingService.getClusterName('app', 'cluster', 'details-withdash')).toBe('app-cluster-details-withdash');
  });

  it('returns sequence if found, else null', function() {
    expect(this.namingService.getSequence('app')).toBe(null);
    expect(this.namingService.getSequence('app-vnope')).toBe(null);
    expect(this.namingService.getSequence('app-v003-no')).toBe(null);
    expect(this.namingService.getSequence('app-v003')).toBe('v003');
    expect(this.namingService.getSequence('app-cluster-v003')).toBe('v003');
    expect(this.namingService.getSequence('app-cluster-details-v003')).toBe('v003');
    expect(this.namingService.getSequence('app--v003')).toBe('v003');
  });
});
