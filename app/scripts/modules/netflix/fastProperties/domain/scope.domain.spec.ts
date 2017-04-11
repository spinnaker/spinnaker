import {IPlatformProperty} from './platformProperty.model';
import {Scope} from './scope.domain';


describe('Scope Domain Spec', function () {

  describe('statically create a new scope from a scope fetched from the props API', () => {
    it('should build a new scope when a we have complete scope from props API ', function () {
      const platformProperty = <IPlatformProperty>{
        env: 'prod',
        region: 'us-east-1',
        appId: 'deck',
        stack: 'main',
        asg: 'asg-v100',
        serverId: 'i-1234',
        zone: 'us-east-1b',
        cluster: 'my-cluster',
        // this should be excluded from the build function
        propertyId: '1234'
      };

      const result: Scope = Scope.build(platformProperty);

      ['env', 'region', 'appId', 'stack', 'asg', 'severId', 'zone', 'cluster'].forEach((prop: string) => {
        expect(result[prop]).toBe(platformProperty[prop]);
      });

      expect(result['propertyId']).toBeUndefined();

    });
  });

  describe('prep a scope for submission', function () {

    it('has no appId', function () {
      const scope = new Scope();

      const readyScope = scope.forSubmit('prod');
      expect(readyScope.appIdList).toEqual([]);
    });

    it('has single appId', function () {
      const scope = new Scope();

      scope.appId = 'deck';

      const readyScope = scope.forSubmit('prod');
      expect(readyScope.appIdList).toEqual(['deck']);
    });

    it('has multiple comma delimited appId', function () {
      const scope = new Scope();

      scope.appId = 'deck,demo';

      const readyScope = scope.forSubmit('prod');
      expect(readyScope.appIdList).toEqual(['deck', 'demo']);
    });

    it('has multiple comma delimited appId with whitespace', function () {
      const scope = new Scope();

      scope.appId = 'deck , demo';

      const readyScope = scope.forSubmit('prod');
      expect(readyScope.appIdList).toEqual(['deck', 'demo']);
    });
  });
});


