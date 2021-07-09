import { mock } from 'angular';
import { mockHttpClient } from '../../api/mock/jasmine';

import Spy = jasmine.Spy;
import { IApplicationDataSourceAttribute, ApplicationReader } from './ApplicationReader';
import { ApplicationDataSourceRegistry } from './ApplicationDataSourceRegistry';
import { Application } from '../application.model';
import { LOAD_BALANCER_DATA_SOURCE } from '../../loadBalancer/loadBalancer.dataSource';
import { LOAD_BALANCER_READ_SERVICE, LoadBalancerReader } from '../../loadBalancer/loadBalancer.read.service';
import { SECURITY_GROUP_READER, SecurityGroupReader } from '../../securityGroup/securityGroupReader.service';
import { CLUSTER_SERVICE, ClusterService } from '../../cluster/cluster.service';
import { SERVER_GROUP_DATA_SOURCE } from '../../serverGroup/serverGroup.dataSource';
import { SECURITY_GROUP_DATA_SOURCE } from '../../securityGroup/securityGroup.dataSource';

describe('ApplicationReader', function () {
  let securityGroupReader: SecurityGroupReader;
  let loadBalancerReader: any;
  let clusterService: ClusterService;
  let $q: ng.IQService;

  beforeEach(() => ApplicationDataSourceRegistry.clearDataSources());

  beforeEach(
    mock.module(
      SECURITY_GROUP_DATA_SOURCE,
      SERVER_GROUP_DATA_SOURCE,
      LOAD_BALANCER_DATA_SOURCE,
      SECURITY_GROUP_READER,
      CLUSTER_SERVICE,
      LOAD_BALANCER_READ_SERVICE,
    ),
  );

  beforeEach(
    mock.inject(function (
      _securityGroupReader_: SecurityGroupReader,
      _clusterService_: ClusterService,
      _$q_: ng.IQService,
      _loadBalancerReader_: LoadBalancerReader,
    ) {
      securityGroupReader = _securityGroupReader_;
      clusterService = _clusterService_;
      loadBalancerReader = _loadBalancerReader_;
      $q = _$q_;
    }),
  );

  describe('load application', function () {
    let application: Application = null;

    async function loadApplication(dataSources?: IApplicationDataSourceAttribute) {
      const http = mockHttpClient();
      const response = { applicationName: 'deck', attributes: {} as any };
      if (dataSources !== undefined) {
        response.attributes['dataSources'] = dataSources;
      }
      http.expectGET('/applications/deck').respond(200, response);
      spyOn(securityGroupReader, 'loadSecurityGroupsByApplicationName').and.returnValue($q.when([]));
      spyOn(loadBalancerReader, 'loadLoadBalancers').and.returnValue($q.when([]));
      spyOn(clusterService, 'loadServerGroups').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'loadSecurityGroups').and.returnValue($q.when([] as any));
      spyOn(securityGroupReader, 'getApplicationSecurityGroups').and.callFake(function (
        _app: Application,
        groupsByName: any,
      ) {
        return $q.when(groupsByName || []);
      });

      ApplicationReader.getApplication('deck').then((app) => {
        application = app;
      });
      await http.flush();
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
