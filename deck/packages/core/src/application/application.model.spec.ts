import { mock } from 'angular';

import type { Application } from './application.model';
import { ApplicationModelBuilder } from './applicationModel.builder';
import type { IEntityTag, IEntityTags, IInstanceCounts, ILoadBalancer, IServerGroup } from '../domain';
import { LOAD_BALANCER_DATA_SOURCE } from '../loadBalancer/loadBalancer.dataSource';
import { SECURITY_GROUP_DATA_SOURCE } from '../securityGroup/securityGroup.dataSource';
import type { SecurityGroupReader } from '../securityGroup/securityGroupReader.service';
import { SERVER_GROUP_DATA_SOURCE } from '../serverGroup/serverGroup.dataSource';
import { ApplicationDataSourceRegistry } from './service/ApplicationDataSourceRegistry';

describe('Application Model', function () {
  let application: Application,
    securityGroupReader: SecurityGroupReader,
    loadBalancerReader: any,
    clusterService: any,
    $rootScope: ng.IRootScopeService;

  beforeEach(() => ApplicationDataSourceRegistry.clearDataSources());

  beforeEach(mock.module(SECURITY_GROUP_DATA_SOURCE, SERVER_GROUP_DATA_SOURCE, LOAD_BALANCER_DATA_SOURCE));

  beforeEach(
    mock.inject(function (
      _securityGroupReader_: SecurityGroupReader,
      _clusterService_: any,
      _loadBalancerReader_: any,
      _$rootScope_: ng.IRootScopeService,
    ) {
      securityGroupReader = _securityGroupReader_;
      clusterService = _clusterService_;
      loadBalancerReader = _loadBalancerReader_;
      $rootScope = _$rootScope_;
    }),
  );

  async function flushPromise<T>(promise: PromiseLike<T>): Promise<T> {
    const nativePromise = Promise.resolve(promise);
    await Promise.resolve();
    $rootScope.$digest();
    return nativePromise;
  }

  async function configureApplication(
    serverGroups: any[],
    loadBalancers: any[],
    securityGroupsByApplicationName: any[],
  ) {
    spyOn(securityGroupReader, 'loadSecurityGroupsByApplicationName').and.returnValue(
      Promise.resolve(securityGroupsByApplicationName),
    );
    spyOn(loadBalancerReader, 'loadLoadBalancers').and.returnValue(Promise.resolve(loadBalancers));
    spyOn(clusterService, 'loadServerGroups').and.returnValue(Promise.resolve(serverGroups));
    spyOn(securityGroupReader, 'loadSecurityGroups').and.returnValue(Promise.resolve([] as any));
    spyOn(securityGroupReader, 'getApplicationSecurityGroups').and.callFake(function (
      _app: Application,
      groupsByName: any[],
    ) {
      return Promise.resolve(groupsByName || []);
    });
    application = ApplicationModelBuilder.createApplicationForTests(
      'app',
      ...ApplicationDataSourceRegistry.getDataSources(),
    );
    await flushPromise(application.refresh());
  }

  describe('lazy dataSources', function () {
    beforeEach(function () {
      ApplicationDataSourceRegistry.registerDataSource({
        key: 'lazySource',
        lazy: true,
        defaultData: [],
        loader: () => Promise.resolve(['a']),
        onLoad: (_app, data) => Promise.resolve(data),
      });
    });

    describe('activate', function () {
      it('refreshes section if not already active and not already loaded', async function () {
        await configureApplication([], [], []);
        spyOn(application.getDataSource('lazySource'), 'refresh').and.callThrough();

        application.getDataSource('lazySource').activate();
        await flushPromise(application.getDataSource('lazySource').ready());
        expect((application.getDataSource('lazySource').refresh as any).calls.count()).toBe(1);
        expect(application.getDataSource('lazySource').active).toBe(true);
        expect(application.getDataSource('lazySource').loaded).toBe(true);

        application.getDataSource('lazySource').deactivate();
        expect(application.getDataSource('lazySource').active).toBe(false);
        application.getDataSource('lazySource').activate();
        // not refreshed since still loaded
        expect(application.getDataSource('lazySource').active).toBe(true);
        expect((application.getDataSource('lazySource').refresh as any).calls.count()).toBe(1);

        application.getDataSource('lazySource').deactivate();
        application.getDataSource('lazySource').loaded = false;
        application.getDataSource('lazySource').activate();
        await flushPromise(application.getDataSource('lazySource').ready());
        expect((application.getDataSource('lazySource').refresh as any).calls.count()).toBe(2);
      });
    });

    describe('refresh behavior', function () {
      it('clears data on inactive lazy dataSources and sets loaded flag to false', async function () {
        await configureApplication([], [], []);

        expect(application.getDataSource('lazySource').active).toBeFalsy();

        application.getDataSource('lazySource').activate();
        await flushPromise(application.getDataSource('lazySource').ready());
        expect(application.getDataSource('lazySource').active).toBe(true);
        expect(application.getDataSource('lazySource').loaded).toBe(true);
        expect(application.getDataSource('lazySource').data.length).toBe(1);

        application.getDataSource('lazySource').deactivate();
        await flushPromise(application.refresh());

        expect(application.getDataSource('lazySource').data).toEqual([]);
        expect(application.getDataSource('lazySource').loaded).toBe(false);
      });

      it('adds entityTags that contain alerts if found on data', async function () {
        const alertTag: IEntityTag = { name: 'spinnaker_ui_alert:alert1', value: { message: 'an alert' } };
        const tags: IEntityTags = {
          id: 'zzzz',
          tags: [alertTag],
          tagsMetadata: null,
          entityRef: null,
          alerts: [alertTag],
          notices: [],
        };
        const nonAlertTags: IEntityTags = {
          id: 'zzzz',
          tags: [{ name: 'spinnaker_ui_notice:notice1', value: { message: 'a notice' } }],
          tagsMetadata: null,
          entityRef: null,
          alerts: [],
          notices: [{ name: 'spinnaker_ui_notice:notice1', value: { message: 'a notice' } }],
        };
        const serverGroups: IServerGroup[] = [
          {
            account: 'test',
            cloudProvider: 'aws',
            cluster: 'myapp',
            instanceCounts: null,
            instances: [],
            name: 'myapp-v001',
            region: 'us-east-1',
            type: 'aws',
            entityTags: tags,
          },
          {
            account: 'test',
            cloudProvider: 'aws',
            cluster: 'myapp',
            instanceCounts: null,
            instances: [],
            name: 'myapp-v001',
            region: 'us-east-1',
            type: 'aws',
            entityTags: nonAlertTags,
          },
          {
            account: 'test',
            cloudProvider: 'aws',
            cluster: 'myapp',
            instanceCounts: null,
            instances: [],
            name: 'myapp-no-alerts-v002',
            region: 'us-east-1',
            type: 'aws',
          },
        ];
        await configureApplication(serverGroups, [], []);
        expect(application.getDataSource('serverGroups').alerts).toEqual([tags]);
      });
    });

    describe('application ready', function () {
      it('ignores lazy dataSources when determining if application is ready', async function () {
        let isReady = false;
        await configureApplication([], [], []);

        application.ready().then(() => (isReady = true));
        await flushPromise(application.ready());
        expect(isReady).toBe(true);
      });
    });
  });

  describe('setting default credentials and regions', function () {
    it('sets default credentials and region from server group when only one account/region found', async function () {
      const serverGroups: IServerGroup[] = [
        {
          name: 'deck-test-v001',
          cluster: 'deck-test',
          account: 'test',
          region: 'us-west-2',
          type: 'aws',
          cloudProvider: 'aws',
          instances: [],
          instanceCounts: {} as IInstanceCounts,
        },
      ];
      const loadBalancers: ILoadBalancer[] = [];
      const securityGroupsByApplicationName: any[] = [];

      await configureApplication(serverGroups, loadBalancers, securityGroupsByApplicationName);
      expect(application.defaultCredentials.aws).toBe('test');
      expect(application.defaultRegions.aws).toBe('us-west-2');
    });

    it('sets default credentials and region from load balancer when only one account/region found', async function () {
      const serverGroups: IServerGroup[] = [];
      const loadBalancers: ILoadBalancer[] = [
        { name: 'deck-frontend', cloudProvider: 'gce', vpcId: 'vpc0', region: 'us-central-1', account: 'prod' },
      ];
      const securityGroupsByApplicationName: any[] = [];

      await configureApplication(serverGroups, loadBalancers, securityGroupsByApplicationName);
      expect(application.defaultCredentials.gce).toBe('prod');
      expect(application.defaultRegions.gce).toBe('us-central-1');
    });

    it('sets default credentials and region from firewall', async function () {
      const serverGroups: any[] = [];
      const loadBalancers: ILoadBalancer[] = [];
      const securityGroupsByApplicationName: any[] = [
        { name: 'deck-test', provider: 'cf', accountName: 'test', region: 'us-south-7' },
      ];

      await configureApplication(serverGroups, loadBalancers, securityGroupsByApplicationName);
      expect(application.defaultCredentials.cf).toBe('test');
      expect(application.defaultRegions.cf).toBe('us-south-7');
    });

    it('does not set defaults when multiple values found for the same provider', async function () {
      const serverGroups: IServerGroup[] = [];
      const loadBalancers: ILoadBalancer[] = [
        { name: 'deck-frontend', cloudProvider: 'aws', vpcId: 'vpcId', region: 'us-west-1', account: 'prod' },
      ];
      const securityGroupsByApplicationName: any[] = [
        { name: 'deck-test', provider: 'aws', accountName: 'test', region: 'us-east-1' },
      ];

      await configureApplication(serverGroups, loadBalancers, securityGroupsByApplicationName);
      expect(application.defaultCredentials.aws).toBeUndefined();
      expect(application.defaultRegions.aws).toBeUndefined();
    });

    it('sets default region or default credentials if possible', async function () {
      const serverGroups: IServerGroup[] = [];
      const loadBalancers: ILoadBalancer[] = [
        { name: 'deck-frontend', cloudProvider: 'aws', vpcId: 'vpcId', region: 'us-east-1', account: 'prod' },
      ];
      const securityGroupsByApplicationName: any[] = [
        { name: 'deck-test', provider: 'aws', accountName: 'test', region: 'us-east-1' },
      ];

      await configureApplication(serverGroups, loadBalancers, securityGroupsByApplicationName);
      expect(application.defaultCredentials.aws).toBeUndefined();
      expect(application.defaultRegions.aws).toBe('us-east-1');
    });

    it('sets default credentials, even if region cannot be set', async function () {
      const serverGroups: IServerGroup[] = [];
      const loadBalancers: ILoadBalancer[] = [
        { name: 'deck-frontend', cloudProvider: 'aws', vpcId: 'vpc0', region: 'us-east-1', account: 'test' },
      ];
      const securityGroupsByApplicationName: any[] = [
        { name: 'deck-test', provider: 'aws', accountName: 'test', region: 'us-west-1' },
      ];

      await configureApplication(serverGroups, loadBalancers, securityGroupsByApplicationName);
      expect(application.defaultCredentials.aws).toBe('test');
      expect(application.defaultRegions.aws).toBeUndefined();
    });

    it('should set defaults for multiple providers', async function () {
      const serverGroups: any[] = [
        {
          name: 'deck-test-v001',
          account: 'test',
          region: 'us-west-2',
          provider: 'aws',
          instances: [],
          instanceCounts: { up: 0, down: 0, starting: 0, unknown: 0, outOfService: 0 },
        },
        {
          name: 'deck-gce-v001',
          account: 'gce-test',
          region: 'us-central-1',
          provider: 'gce',
          instances: [],
          instanceCounts: { up: 0, down: 0, starting: 0, unknown: 0, outOfService: 0 },
        },
      ];
      const loadBalancers: ILoadBalancer[] = [
        {
          name: 'deck-frontend',
          account: 'gce-test',
          cloudProvider: 'gce',
          region: 'us-central-1',
          serverGroups: [],
        },
      ];
      const securityGroupsByApplicationName: any[] = [
        { name: 'deck-test', provider: 'aws', accountName: 'test', region: 'us-west-2' },
      ];

      await configureApplication(serverGroups, loadBalancers, securityGroupsByApplicationName);
      expect(application.defaultCredentials.aws).toBe('test');
      expect(application.defaultRegions.aws).toBe('us-west-2');
      expect(application.defaultCredentials.gce).toBe('gce-test');
      expect(application.defaultRegions.gce).toBe('us-central-1');
    });
  });
});
