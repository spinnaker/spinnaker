import { ApplicationDataSourceRegistry } from './ApplicationDataSourceRegistry';
import type { IApplicationDataSourceAttribute } from './ApplicationReader';
import { ApplicationReader } from './ApplicationReader';
import { mockHttpClient } from '../../api/mock/jasmine';
import type { Application } from '../application.model';
import type { ClusterService } from '../../cluster/cluster.service';
import { registerLoadBalancerDataSource } from '../../loadBalancer/loadBalancer.dataSource';
import type { LoadBalancerReader } from '../../loadBalancer/loadBalancer.read.service';
import { registerSecurityGroupDataSource } from '../../securityGroup/securityGroup.dataSource';
import type { SecurityGroupReader } from '../../securityGroup/securityGroupReader.service';
import { registerServerGroupDataSource } from '../../serverGroup/serverGroup.dataSource';
import { nativePromiseService } from '../../utils/nativePromiseService';

import Spy = jasmine.Spy;

describe('ApplicationReader', function () {
  let securityGroupReader: SecurityGroupReader;
  let loadBalancerReader: LoadBalancerReader;
  let clusterService: ClusterService;

  beforeEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
    securityGroupReader = {
      loadSecurityGroupsByApplicationName: () => Promise.resolve([]),
      loadSecurityGroups: () => Promise.resolve([]),
      getApplicationSecurityGroups: () => Promise.resolve([]),
    } as any;
    loadBalancerReader = { loadLoadBalancers: () => Promise.resolve([]) } as any;
    clusterService = {
      loadServerGroups: () => Promise.resolve([]),
      createServerGroupClusters: () => [],
      addServerGroupsToApplication: (_application: Application, serverGroups: any[]) => serverGroups,
      addTasksToServerGroups: () => undefined,
      addExecutionsToServerGroups: () => undefined,
    } as any;
    registerSecurityGroupDataSource(nativePromiseService, securityGroupReader);
    registerServerGroupDataSource(nativePromiseService, clusterService);
    registerLoadBalancerDataSource(nativePromiseService, loadBalancerReader);
  });

  afterEach(() => {
    ApplicationDataSourceRegistry.clearDataSources();
  });

  describe('load application', function () {
    let application: Application = null;

    async function loadApplication(dataSources?: IApplicationDataSourceAttribute) {
      const http = mockHttpClient();
      const response = { applicationName: 'deck', attributes: {} as any };
      if (dataSources !== undefined) {
        response.attributes['dataSources'] = dataSources;
      }
      http.expectGET('/applications/deck').respond(200, response);
      spyOn(securityGroupReader, 'loadSecurityGroupsByApplicationName').and.returnValue(Promise.resolve([]));
      spyOn(loadBalancerReader, 'loadLoadBalancers').and.returnValue(Promise.resolve([]));
      spyOn(clusterService, 'loadServerGroups').and.returnValue(Promise.resolve([]));
      spyOn(securityGroupReader, 'loadSecurityGroups').and.returnValue(Promise.resolve([] as any));
      spyOn(securityGroupReader, 'getApplicationSecurityGroups').and.callFake(function (
        _app: Application,
        groupsByName: any,
      ) {
        return Promise.resolve(groupsByName || []);
      });

      const applicationPromise = ApplicationReader.getApplication('deck');
      await http.flush();
      application = await applicationPromise;
    }

    it('loads all data sources if dataSource attribute is missing', async function () {
      await loadApplication();
      expect(application.attributes.dataSources).toBeUndefined();
      expect((clusterService.loadServerGroups as Spy).calls.count()).toBe(1);
      expect((securityGroupReader.getApplicationSecurityGroups as Spy).calls.count()).toBe(1);
      expect(loadBalancerReader.loadLoadBalancers.calls.count()).toBe(1);
    });

    it('loads all data sources if disabled dataSource attribute is an empty array', async function () {
      await loadApplication({ enabled: [], disabled: [] });
      expect((clusterService.loadServerGroups as Spy).calls.count()).toBe(1);
      expect((securityGroupReader.getApplicationSecurityGroups as Spy).calls.count()).toBe(1);
      expect(loadBalancerReader.loadLoadBalancers.calls.count()).toBe(1);
    });

    it('only loads configured dataSources if attribute is non-empty', async function () {
      const dataSources = { enabled: ['serverGroups'], disabled: ['securityGroups', 'loadBalancers'] };
      await loadApplication(dataSources);
      expect((clusterService.loadServerGroups as Spy).calls.count()).toBe(1);
      expect((securityGroupReader.getApplicationSecurityGroups as Spy).calls.count()).toBe(0);
      expect(loadBalancerReader.loadLoadBalancers.calls.count()).toBe(0);

      expect(application.getDataSource('serverGroups').disabled).toBe(false);
      expect(application.getDataSource('loadBalancers').disabled).toBe(true);
      expect(application.getDataSource('securityGroups').disabled).toBe(true);
    });

    describe('opt-in data sources', function () {
      beforeEach(function () {
        ApplicationDataSourceRegistry.registerDataSource({
          key: 'optInSource',
          visible: true,
          optional: true,
          optIn: true,
          defaultData: [],
        });
      });

      it('disables opt-in data sources when nothing configured on application dataSources attribute', async function () {
        await loadApplication();
        expect(application.getDataSource('optInSource').disabled).toBe(true);
      });

      it('disables opt-in data sources when nothing configured on application dataSources.disabled attribute', async function () {
        await loadApplication({ enabled: [], disabled: [] });
        expect(application.getDataSource('optInSource').disabled).toBe(true);
      });

      it('enables opt-in data source when configured on application dataSources.disabled attribute', async function () {
        await loadApplication({ enabled: ['optInSource'], disabled: [] });
        expect(application.getDataSource('optInSource').disabled).toBe(false);
      });
    });
  });
});
