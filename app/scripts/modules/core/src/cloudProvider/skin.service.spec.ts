import { IQService, IRootScopeService, IScope, mock } from 'angular';
import { ApplicationModelBuilder } from 'core/application';

import { SkinService } from './skin.service';

describe('Service: SkinService', () => {
  let scope: IScope, $q: IQService;

  beforeEach(
    mock.inject(($rootScope: IRootScopeService, _$q_: IQService) => {
      scope = $rootScope.$new();
      $q = _$q_;
    }),
  );

  describe('instance skin disambiguation', () => {
    beforeEach(() => {
      spyOn(SkinService, 'getAccounts').and.returnValue(
        $q.resolve([
          { name: 'v1-k8s-account', cloudProvider: 'kubernetes', skin: 'v1' },
          { name: 'v2-k8s-account', cloudProvider: 'kubernetes', skin: 'v2' },
          { name: 'appengine-account', cloudProvider: 'appengine', skin: 'v1' },
          { name: 'gce-account', cloudProvider: 'gce' },
        ]),
      );
    });

    it('uses available accounts to determine skin if possible', () => {
      const app = ApplicationModelBuilder.createStandaloneApplication('myApp');

      SkinService.getInstanceSkin('appengine', 'my-instance-id', app).then(skin => {
        expect(skin).toEqual('v1');
      });
      SkinService.getInstanceSkin('gce', 'my-instance-id', app).then(skin => {
        expect(skin).toEqual(null);
      });

      scope.$digest();
    });

    it('scrapes application server groups to determine skin if possible', () => {
      const app = ApplicationModelBuilder.createApplicationForTests(
        'myApp',
        {
          key: 'serverGroups',
          loader: () =>
            $q.resolve([
              {
                name: 'myServerGroup',
                account: 'v2-k8s-account',
                cloudProvider: 'kubernetes',
                instances: [{ id: 'my-instance-id' }],
                serverGroups: [],
              },
            ]),
          onLoad: (_app, data) => $q.resolve(data),
        },
        {
          key: 'loadBalancers',
        },
      );

      SkinService.getInstanceSkin('kubernetes', 'my-instance-id', app).then(skin => {
        expect(skin).toEqual('v2');
      });

      scope.$digest();
    });

    it('scrapes application load balancers to determine skin if possible', () => {
      const app = ApplicationModelBuilder.createApplicationForTests(
        'myApp',
        {
          key: 'loadBalancers',
          loader: () =>
            $q.resolve([
              {
                name: 'myLoadBalancer',
                account: 'v2-k8s-account',
                cloudProvider: 'kubernetes',
                instances: [{ id: 'my-instance-id' }],
              },
            ]),
          onLoad: (_app, data) => $q.resolve(data),
        },
        {
          key: 'serverGroups',
        },
      );

      SkinService.getInstanceSkin('kubernetes', 'my-instance-id', app).then(skin => {
        expect(skin).toEqual('v2');
      });

      scope.$digest();
    });

    it("scrapes application load balancers' server groups to determine skin if possible", () => {
      const app = ApplicationModelBuilder.createApplicationForTests(
        'myApp',
        {
          key: 'loadBalancers',
          loader: () =>
            $q.resolve([
              {
                name: 'myLoadBalancer',
                account: 'v2-k8s-account',
                cloudProvider: 'kubernetes',
                instances: [],
                serverGroups: [
                  {
                    isDisabled: true,
                    instances: [{ id: 'my-instance-id' }],
                  },
                ],
              },
            ]),
          onLoad: (_app, data) => $q.resolve(data),
        },
        {
          key: 'serverGroups',
        },
      );

      SkinService.getInstanceSkin('kubernetes', 'my-instance-id', app).then(skin => {
        expect(skin).toEqual('v2');
      });

      scope.$digest();
    });
  });
});
