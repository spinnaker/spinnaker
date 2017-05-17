import {
  Application,
  APPLICATION_DATA_SOURCE_REGISTRY,
  APPLICATION_MODEL_BUILDER,
  ApplicationModelBuilder
} from '@spinnaker/core';
import { mock } from 'angular';

import { NetflixSettings } from '../netflix.settings';
import { CiBuildReader } from './services/ciBuild.read.service';

describe('CI Data Source', function () {
  beforeEach(function () {
    NetflixSettings.feature.netflixMode = true;
  });

  beforeEach(
    mock.module(
      require('./ci.dataSource'),
      APPLICATION_MODEL_BUILDER,
      APPLICATION_DATA_SOURCE_REGISTRY
    )
  );

  beforeEach(
    mock.inject(
      function(ciBuildReader: CiBuildReader, $httpBackend: ng.IHttpBackendService, $q: ng.IQService,
               applicationModelBuilder: ApplicationModelBuilder, $rootScope: ng.IRootScopeService,
               applicationDataSourceRegistry: any) {
        this.ciBuildReader = ciBuildReader;
        this.applicationDataSourceRegistry = applicationDataSourceRegistry;
        this.$http = $httpBackend;
        this.$q = $q;
        this.builder = applicationModelBuilder;
        this.$scope = $rootScope.$new();
        this.applicationModelBuilder = applicationModelBuilder;
        applicationDataSourceRegistry.registerDataSource({key: 'ci'});
      }
    )
  );

  afterEach(NetflixSettings.resetToOriginal);

  describe('load builds', function () {
    it('only calls build service when all configuration values are present', function () {
      const application: Application = this.applicationModelBuilder.createApplication(this.applicationDataSourceRegistry.getDataSources());
      const dataSource = application.getDataSource('ci');

      spyOn(this.ciBuildReader, 'getBuilds').and.callFake(() => {
        return this.$q.when([{id: 'a'}]);
      });
      dataSource.activate();
      this.$scope.$digest();
      expect(this.ciBuildReader.getBuilds.calls.count()).toBe(0);
      expect(dataSource.data.length).toBe(0);

      application.attributes.repoType = 'stash';
      dataSource.refresh();
      this.$scope.$digest();
      expect(this.ciBuildReader.getBuilds.calls.count()).toBe(0);
      expect(dataSource.data.length).toBe(0);

      application.attributes.repoProjectKey = 'spinnaker';
      dataSource.refresh();
      this.$scope.$digest();
      expect(this.ciBuildReader.getBuilds.calls.count()).toBe(0);
      expect(dataSource.data.length).toBe(0);

      application.attributes.repoSlug = 'deck';
      dataSource.refresh();
      this.$scope.$digest();
      expect(this.ciBuildReader.getBuilds.calls.count()).toBe(1);
      expect(dataSource.data).toEqual([{id: 'a'}]);
    });
  });

});
