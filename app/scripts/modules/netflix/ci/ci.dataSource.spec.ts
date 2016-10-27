import {Application} from 'core/application/application.model';
import APPLICATION_MODEL_BUILDER, {ApplicationModelBuilder} from '../../core/application/applicationModel.builder';
import IProvideService = angular.auto.IProvideService;
import DATA_SOURCE_REGISTRY from 'core/application/service/applicationDataSource.registry';

describe('CI Data Source', function () {
  beforeEach(
    angular.mock.module(
      require('./ci.dataSource'),
      APPLICATION_MODEL_BUILDER,
      DATA_SOURCE_REGISTRY,
      function($provide: IProvideService) {
        return $provide.constant('settings', {
          feature: { netflixMode: true }
        });
      }
    )
  );

  beforeEach(
    angular.mock.inject(
      function(buildService: any, $httpBackend: ng.IHttpBackendService, settings: any, $q: ng.IQService,
               applicationModelBuilder: ApplicationModelBuilder, $rootScope: ng.IRootScopeService,
               applicationDataSourceRegistry: any) {
        this.buildService = buildService;
        this.applicationDataSourceRegistry = applicationDataSourceRegistry;
        this.$http = $httpBackend;
        this.$q = $q;
        this.settings = settings;
        this.builder = applicationModelBuilder;
        this.$scope = $rootScope.$new();
        this.applicationModelBuilder = applicationModelBuilder;
        applicationDataSourceRegistry.registerDataSource({key: 'ci'});
      }
    )
  );

  describe('load builds', function () {
    it('only calls build service when all configuration values are present', function () {
      const application: Application = this.applicationModelBuilder.createApplication(this.applicationDataSourceRegistry.getDataSources());
      const dataSource = application.getDataSource('ci');

      spyOn(this.buildService, 'getBuilds').and.callFake(() => {
        return this.$q.when([{id: 'a'}]);
      });
      dataSource.activate();
      this.$scope.$digest();
      expect(this.buildService.getBuilds.calls.count()).toBe(0);
      expect(dataSource.data.length).toBe(0);

      application.attributes.repoType = 'stash';
      dataSource.refresh();
      this.$scope.$digest();
      expect(this.buildService.getBuilds.calls.count()).toBe(0);
      expect(dataSource.data.length).toBe(0);

      application.attributes.repoProjectKey = 'spinnaker';
      dataSource.refresh();
      this.$scope.$digest();
      expect(this.buildService.getBuilds.calls.count()).toBe(0);
      expect(dataSource.data.length).toBe(0);

      application.attributes.repoSlug = 'deck';
      dataSource.refresh();
      this.$scope.$digest();
      expect(this.buildService.getBuilds.calls.count()).toBe(1);
      expect(dataSource.data).toEqual([{id: 'a'}]);
    });
  });

});
