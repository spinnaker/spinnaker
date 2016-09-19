describe('Service: applicationReader', function () {

  var applicationReader;
  var securityGroupReader;
  var loadBalancerReader;
  var clusterService;
  var $http;
  var $q;
  var $scope;
  var settings;
  var applicationDataSourceRegistry;

  beforeEach(
    window.module(
      require('./applications.read.service'),
      require('../../securityGroup/securityGroup.dataSource'),
      require('../../serverGroup/serverGroup.dataSource'),
      require('../../loadBalancer/loadBalancer.dataSource'),
      require('../../securityGroup/securityGroup.read.service'),
      require('../../cluster/cluster.service'),
      require('../../loadBalancer/loadBalancer.read.service')
    )
  );

  beforeEach(
    window.inject(function (_applicationReader_, _securityGroupReader_, _clusterService_, $httpBackend, _$q_,
                            _loadBalancerReader_, $rootScope, _settings_, _applicationDataSourceRegistry_) {
      applicationReader = _applicationReader_;
      securityGroupReader = _securityGroupReader_;
      clusterService = _clusterService_;
      loadBalancerReader = _loadBalancerReader_;
      $http = $httpBackend;
      $q = _$q_;
      $scope = $rootScope.$new();
      settings = _settings_;
      applicationDataSourceRegistry = _applicationDataSourceRegistry_;
    })
  );

  describe('load application', function () {

    var application = null;

    function loadApplication(dataSources) {
      let response = {applicationName: 'deck', attributes: {}};
      if (dataSources !== undefined) {
        response.attributes.dataSources = dataSources;
      }
      $http.expectGET(settings.gateUrl + '/applications/deck').respond(200, response);
      spyOn(securityGroupReader, 'loadSecurityGroupsByApplicationName').and.returnValue($q.when([]));
      spyOn(loadBalancerReader, 'loadLoadBalancers').and.returnValue($q.when([]));
      spyOn(clusterService, 'loadServerGroups').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'loadSecurityGroups').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'getApplicationSecurityGroups').and.callFake(function(app, groupsByName) {
        return $q.when(groupsByName || []);
      });

      applicationReader.getApplication('deck').then(app => {
        application = app;
      });
      $scope.$digest();
      $http.flush();
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

      expect(application.serverGroups.disabled).toBe(false);
      expect(application.loadBalancers.disabled).toBe(true);
      expect(application.securityGroups.disabled).toBe(true);
    });

    describe('opt-in data sources', function () {
      beforeEach(function () {
        applicationDataSourceRegistry.registerDataSource({ key: 'optInSource', visible: true, optional: true, optIn: true });
      });

      it('disables opt-in data sources when nothing configured on application dataSources attribute', function () {
        loadApplication();
        expect(application.optInSource.disabled).toBe(true);
      });

      it('disables opt-in data sources when nothing configured on application dataSources.disabled attribute', function () {
        loadApplication({enabled: [], disabled: []});
        expect(application.optInSource.disabled).toBe(true);
      });

      it('enables opt-in data source when configured on application dataSources.disabled attribute', function () {
        loadApplication({enabled: ['optInSource'], disabled: []});
        expect(application.optInSource.disabled).toBe(false);
      });
    });

  });

});
