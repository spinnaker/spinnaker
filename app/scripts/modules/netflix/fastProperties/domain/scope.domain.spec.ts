import {IPlatformProperty} from './platformProperty.model';
import {Scope} from './scope.domain';


describe('Scope Domain Spec', function () {

  describe('statically create a new scope from a scope fetched from the props API', () => {
    it('should build a new scope when a we have complete scope from props API ', function () {
      let platformProperty = <IPlatformProperty>{
        env: 'prod',
        region: 'us-east-1',
        appId: 'deck',
        stack: 'main',
        asg: 'asg-v100',
        serverId: 'i-1234',
        zone: 'us-east-1b',
        cluster: 'my-cluster'
      };

      let result: Scope = Scope.build(platformProperty);

      ['env', 'region', 'appId', 'stack', 'asg', 'severId', 'zone', 'cluster'].forEach((prop: string) => {
        expect(result[prop]).toBe(platformProperty[prop]);
      });

    });
  });
});


