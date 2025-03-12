import { NameUtils } from './nameUtils';

describe('nameUtils', function () {
  describe('parseServerGroupName', function () {
    it('parses server group name with no stack or details', function () {
      expect(NameUtils.parseServerGroupName('app-v001')).toEqual({
        application: 'app',
        stack: '',
        freeFormDetails: '',
        cluster: 'app',
      });
      expect(NameUtils.parseServerGroupName('app-test-v001')).toEqual({
        application: 'app',
        stack: 'test',
        freeFormDetails: '',
        cluster: 'app-test',
      });
      expect(NameUtils.parseServerGroupName('app--detail-v001')).toEqual({
        application: 'app',
        stack: '',
        freeFormDetails: 'detail',
        cluster: 'app--detail',
      });
      expect(NameUtils.parseServerGroupName('app--detail-withdashes-v001')).toEqual({
        application: 'app',
        stack: '',
        freeFormDetails: 'detail-withdashes',
        cluster: 'app--detail-withdashes',
      });
    });

    it('parses server group name with no version', function () {
      expect(NameUtils.parseServerGroupName('app')).toEqual({
        application: 'app',
        stack: '',
        freeFormDetails: '',
        cluster: 'app',
      });
      expect(NameUtils.parseServerGroupName('app-test')).toEqual({
        application: 'app',
        stack: 'test',
        freeFormDetails: '',
        cluster: 'app-test',
      });
      expect(NameUtils.parseServerGroupName('app--detail')).toEqual({
        application: 'app',
        stack: '',
        freeFormDetails: 'detail',
        cluster: 'app--detail',
      });
      expect(NameUtils.parseServerGroupName('app--detail-withdashes')).toEqual({
        application: 'app',
        stack: '',
        freeFormDetails: 'detail-withdashes',
        cluster: 'app--detail-withdashes',
      });
    });
  });

  describe('parseLoadBalancerName', function () {
    it('parses name with no stack or details', function () {
      expect(NameUtils.parseLoadBalancerName('app')).toEqual({
        application: 'app',
        stack: '',
        freeFormDetails: '',
        cluster: 'app',
      });
      expect(NameUtils.parseLoadBalancerName('app-test')).toEqual({
        application: 'app',
        stack: 'test',
        freeFormDetails: '',
        cluster: 'app-test',
      });
      expect(NameUtils.parseLoadBalancerName('app--detail')).toEqual({
        application: 'app',
        stack: '',
        freeFormDetails: 'detail',
        cluster: 'app--detail',
      });
      expect(NameUtils.parseLoadBalancerName('app--detail-withdashes')).toEqual({
        application: 'app',
        stack: '',
        freeFormDetails: 'detail-withdashes',
        cluster: 'app--detail-withdashes',
      });
    });
  });

  it('returns cluster name', function () {
    expect(NameUtils.getClusterName('app', null, null)).toBe('app');
    expect(NameUtils.getClusterName('app', 'cluster', null)).toBe('app-cluster');
    expect(NameUtils.getClusterName('app', null, 'details')).toBe('app--details');
    expect(NameUtils.getClusterName('app', null, 'details-withdash')).toBe('app--details-withdash');
    expect(NameUtils.getClusterName('app', 'cluster', 'details')).toBe('app-cluster-details');
    expect(NameUtils.getClusterName('app', 'cluster', 'details-withdash')).toBe('app-cluster-details-withdash');
  });

  it('returns sequence if found, else null', function () {
    expect(NameUtils.getSequence(0)).toBe('v000');
    expect(NameUtils.getSequence(10)).toBe('v010');
    expect(NameUtils.getSequence(100)).toBe('v100');
    expect(NameUtils.getSequence(null)).toBe(null);
    expect(NameUtils.getSequence(undefined)).toBe(null);
  });
});
