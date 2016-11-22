import {IApplicationDataSourceAttribute, ApplicationReader, APPLICATION_READ_SERVICE} from './applications.read.service';
import {Api} from '../../api/api.service';
import {ApplicationDataSourceRegistry} from './applicationDataSource.registry';
import {Application} from '../application.model';
describe('Service: applicationReader', function () {

  let applicationReader: ApplicationReader;
  let securityGroupReader: any;
  let loadBalancerReader: any;
  let clusterService: any;
  let API: Api;
  let $q: ng.IQService;
  let $scope: ng.IScope;
  let applicationDataSourceRegistry: ApplicationDataSourceRegistry;

  beforeEach(
    angular.mock.module(
      APPLICATION_READ_SERVICE,
      require('../../securityGroup/securityGroup.dataSource'),
      require('../../serverGroup/serverGroup.dataSource'),
      require('../../loadBalancer/loadBalancer.dataSource'),
      require('../../securityGroup/securityGroup.read.service'),
      require('../../cluster/cluster.service'),
      require('../../loadBalancer/loadBalancer.read.service')
    )
  );

  beforeEach(
    angular.mock.inject(function (_applicationReader_: ApplicationReader, _securityGroupReader_: any,
                                  _clusterService_: any, _API_: Api, _$q_: ng.IQService,
                                  _loadBalancerReader_: any, $rootScope: ng.IRootScopeService,
                                  _applicationDataSourceRegistry_: ApplicationDataSourceRegistry) {
      applicationReader = _applicationReader_;
      securityGroupReader = _securityGroupReader_;
      clusterService = _clusterService_;
      loadBalancerReader = _loadBalancerReader_;
      $q = _$q_;
      API = _API_;
      $scope = $rootScope.$new();
      applicationDataSourceRegistry = _applicationDataSourceRegistry_;
    })
  );

  describe('load application', function () {

    let application: Application = null;

    function loadApplication(dataSources?: IApplicationDataSourceAttribute) {
      let response = {applicationName: 'deck', attributes: {} as any};
      if (dataSources !== undefined) {
        response.attributes['dataSources'] = dataSources;
      }
      spyOn(API, 'one').and.returnValue({ get: () => $q.when(response) });
      spyOn(securityGroupReader, 'loadSecurityGroupsByApplicationName').and.returnValue($q.when([]));
      spyOn(loadBalancerReader, 'loadLoadBalancers').and.returnValue($q.when([]));
      spyOn(clusterService, 'loadServerGroups').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'loadSecurityGroups').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'getApplicationSecurityGroups').and.callFake(function(app: Application, groupsByName: any) {
        return $q.when(groupsByName || []);
      });

      applicationReader.getApplication('deck').then(app => {
        application = app;
      });
      $scope.$digest();
    }

    it ('loads all data sources if dataSource attribute is missing', function () {
      loadApplication();
      expect(application.attributes.dataSources).toBeUndefined();
      expect(clusterService.loadServerGroups.calls.count()).toBe(1);
      expect(securityGroupReader.getApplicationSecurityGroups.calls.count()).toBe(1);
      expect(loadBalancerReader.loadLoadBalancers.calls.count()).toBe(1);
    });

    it ('loads all data sources if disabled dataSource attribute is an empty array', function () {
      loadApplication({ enabled: [], disabled: []});
      expect(clusterService.loadServerGroups.calls.count()).toBe(1);
      expect(securityGroupReader.getApplicationSecurityGroups.calls.count()).toBe(1);
      expect(loadBalancerReader.loadLoadBalancers.calls.count()).toBe(1);
    });

    it ('only loads configured dataSources if attribute is non-empty', function () {
      let dataSources = { enabled: ['serverGroups'], disabled: ['securityGroups', 'loadBalancers'] };
      loadApplication(dataSources);
      expect(clusterService.loadServerGroups.calls.count()).toBe(1);
      expect(securityGroupReader.getApplicationSecurityGroups.calls.count()).toBe(0);
      expect(loadBalancerReader.loadLoadBalancers.calls.count()).toBe(0);

      expect(application.getDataSource('serverGroups').disabled).toBe(false);
      expect(application.getDataSource('loadBalancers').disabled).toBe(true);
      expect(application.getDataSource('securityGroups').disabled).toBe(true);
    });

    describe('opt-in data sources', function () {
      beforeEach(function () {
        applicationDataSourceRegistry.registerDataSource({ key: 'optInSource', visible: true, optional: true, optIn: true });
      });

      it('disables opt-in data sources when nothing configured on application dataSources attribute', function () {
        loadApplication();
        expect(application.getDataSource('optInSource').disabled).toBe(true);
      });

      it('disables opt-in data sources when nothing configured on application dataSources.disabled attribute', function () {
        loadApplication({enabled: [], disabled: []});
        expect(application.getDataSource('optInSource').disabled).toBe(true);
      });

      it('enables opt-in data source when configured on application dataSources.disabled attribute', function () {
        loadApplication({enabled: ['optInSource'], disabled: []});
        expect(application.getDataSource('optInSource').disabled).toBe(false);
      });
    });

  });

});
