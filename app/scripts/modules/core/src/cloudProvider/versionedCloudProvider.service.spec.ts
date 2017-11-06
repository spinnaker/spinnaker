import { mock, IRootScopeService, IScope } from 'angular';

import { CLOUD_PROVIDER_REGISTRY } from './cloudProvider.registry';
import { ACCOUNT_SERVICE } from 'core/account/account.service';
import { VERSIONED_CLOUD_PROVIDER_SERVICE, VersionedCloudProviderService } from './versionedCloudProvider.service';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application';

describe('Service: versionedCloudProviderService', () => {
  let service: VersionedCloudProviderService, appBuilder: ApplicationModelBuilder, scope: IScope;

  beforeEach((mock.module(
    VERSIONED_CLOUD_PROVIDER_SERVICE,
    APPLICATION_MODEL_BUILDER,
    CLOUD_PROVIDER_REGISTRY,
    ACCOUNT_SERVICE,
  )));

  beforeEach(
    mock.inject(($rootScope: IRootScopeService,
                 versionedCloudProviderService: VersionedCloudProviderService,
                 applicationModelBuilder: ApplicationModelBuilder) => {
      service = versionedCloudProviderService;
      appBuilder = applicationModelBuilder;
      scope = $rootScope.$new();
    }
  ));

  describe('instance provider version disambiguation', () => {
    beforeEach(() => {
      (service as any).accounts = [
        { name: 'v1-k8s-account', cloudProvider: 'kubernetes', providerVersion: 'v1' },
        { name: 'v2-k8s-account', cloudProvider: 'kubernetes', providerVersion: 'v2' },
        { name: 'appengine-account', cloudProvider: 'appengine', providerVersion: 'v1' },
        { name: 'gce-account', cloudProvider: 'gce' },
      ] as any[];
    });

    it('uses available accounts to determine provider version if possible', () => {
      const app = appBuilder.createStandaloneApplication('myApp');

      service.getInstanceProviderVersion('appengine', 'my-instance-id', app).then(providerVersion => {
        expect(providerVersion).toEqual('v1');
      });
      service.getInstanceProviderVersion('gce', 'my-instance-id', app).then(providerVersion => {
        expect(providerVersion).toEqual(null);
      });

      scope.$digest();
    });

    it('scrapes application server groups to determine provider version if possible', () => {
      const app = appBuilder.createApplication('myApp', [
        {
          key: 'serverGroups',
          data: [
            {
              name: 'myServerGroup',
              account: 'v2-k8s-account',
              cloudProvider: 'kubernetes',
              instances: [
                { id: 'my-instance-id' },
              ],
              serverGroups: [],
            }
          ]
        },
        {
          key: 'loadBalancers',
          data: [],
        }
      ]);

      service.getInstanceProviderVersion('kubernetes', 'my-instance-id', app).then(providerVersion => {
        expect(providerVersion).toEqual('v2');
      });

      scope.$digest();
    });

    it('scrapes application load balancers to determine provider version if possible', () => {
      const app = appBuilder.createApplication('myApp', [
        {
          key: 'loadBalancers',
          data: [
            {
              name: 'myLoadBalancer',
              account: 'v2-k8s-account',
              cloudProvider: 'kubernetes',
              instances: [
                { id: 'my-instance-id' },
              ]
            }
          ]
        },
        {
          key: 'serverGroups',
          data: [],
        },
      ]);

      service.getInstanceProviderVersion('kubernetes', 'my-instance-id', app).then(providerVersion => {
        expect(providerVersion).toEqual('v2');
      });

      scope.$digest();
    });

    it('scrapes application load balancers\' server groups to determine provider version if possible', () => {
      const app = appBuilder.createApplication('myApp', [
        {
          key: 'loadBalancers',
          data: [
            {
              name: 'myLoadBalancer',
              account: 'v2-k8s-account',
              cloudProvider: 'kubernetes',
              instances: [],
              serverGroups: [{
                isDisabled: true,
                instances: [{ id: 'my-instance-id' }],
              }],
            }
          ]
        },
        {
          key: 'serverGroups',
          data: [],
        },
      ]);

      service.getInstanceProviderVersion('kubernetes', 'my-instance-id', app).then(providerVersion => {
        expect(providerVersion).toEqual('v2');
      });

      scope.$digest();
    });
  });
});
