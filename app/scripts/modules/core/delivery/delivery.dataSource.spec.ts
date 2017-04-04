import {IQProvider, mock} from 'angular';

import {Application} from '../application/application.model';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from 'core/application/applicationModel.builder';
import {APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry} from '../application/service/applicationDataSource.registry';
import {EXECUTION_SERVICE} from './service/execution.service';
import {PIPELINE_CONFIG_SERVICE, PipelineConfigService} from 'core/pipeline/config/services/pipelineConfig.service';

describe('Delivery Data Source', function () {

  let application: Application,
      executionService: any,
      pipelineConfigService: PipelineConfigService,
      $scope: ng.IScope,
      applicationModelBuilder: ApplicationModelBuilder,
      applicationDataSourceRegistry: ApplicationDataSourceRegistry,
      $q: ng.IQService;

  beforeEach(
    mock.module(
      require('./delivery.dataSource'),
      EXECUTION_SERVICE,
      PIPELINE_CONFIG_SERVICE,
      APPLICATION_DATA_SOURCE_REGISTRY,
      APPLICATION_MODEL_BUILDER
  ));

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$q
  beforeEach(
    mock.module(($qProvider: IQProvider) => {
      $qProvider.errorOnUnhandledRejections(false);
  }));

  beforeEach(
    mock.inject(function (_executionService_: any, _pipelineConfigService_: PipelineConfigService, _$q_: ng.IQService,
                          $rootScope: ng.IRootScopeService, _applicationModelBuilder_: ApplicationModelBuilder,
                          _applicationDataSourceRegistry_: ApplicationDataSourceRegistry) {
      $q = _$q_;
      $scope = $rootScope.$new();
      executionService = _executionService_;
      pipelineConfigService = _pipelineConfigService_;
      applicationModelBuilder = _applicationModelBuilder_;
      applicationDataSourceRegistry = _applicationDataSourceRegistry_;
    })
  );

  function configureApplication() {
    applicationDataSourceRegistry.registerDataSource({key: 'serverGroups'});
    application = applicationModelBuilder.createApplication(applicationDataSourceRegistry.getDataSources());
    application.refresh();
    $scope.$digest();
  }

  describe('loading executions', function () {
    beforeEach(function () {
      spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
      spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
      spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
    });

    it('loads executions and sets appropriate flags', function () {
      spyOn(executionService, 'getExecutions').and.returnValue($q.when([{status: 'SUCCEEDED', stages: []}]));
      configureApplication();
      application.getDataSource('executions').activate();
      $scope.$digest();
      expect(application.getDataSource('executions').loaded).toBe(true);
      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(false);
    });

    it('sets appropriate flags when execution load fails', function () {
      spyOn(executionService, 'getExecutions').and.returnValue($q.reject(null));
      configureApplication();
      application.getDataSource('executions').activate();
      $scope.$digest();
      expect(application.getDataSource('executions').loaded).toBe(false);
      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(true);
    });
  });

  describe('reload executions', function () {
    beforeEach(function () {
      spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
      spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
      spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
    });

    it('reloads executions and sets appropriate flags', function () {
      spyOn(executionService, 'getExecutions').and.returnValue($q.when([]));
      configureApplication();
      application.getDataSource('executions').activate();
      $scope.$digest();
      expect(application.getDataSource('executions').loaded).toBe(true);
      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(false);

      application.getDataSource('executions').refresh();
      expect(application.getDataSource('executions').loading).toBe(true);

      $scope.$digest();
      expect(application.getDataSource('executions').loaded).toBe(true);
      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(false);

    });

    it('sets appropriate flags when executions reload fails; subscriber is responsible for error checking', function () {
      spyOn(executionService, 'getExecutions').and.returnValue($q.reject(null));
      let errorsHandled = 0,
          successesHandled = 0;
      configureApplication();
      application.getDataSource('executions').activate();

      application.getDataSource('executions').onRefresh($scope, () => successesHandled++, () => errorsHandled++);

      application.getDataSource('executions').refresh();
      $scope.$digest();

      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(true);

      application.getDataSource('executions').refresh();
      $scope.$digest();

      expect(successesHandled).toBe(0);
      expect(errorsHandled).toBe(2);
    });
  });

  describe('loading pipeline configs', function () {
    beforeEach(function () {
      spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
      spyOn(executionService, 'getExecutions').and.returnValue($q.when([]));
    });

    it('loads configs and sets appropriate flags', function () {
      spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
      spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
      configureApplication();
      application.getDataSource('pipelineConfigs').activate();

      application.getDataSource('pipelineConfigs').refresh();
      expect(application.getDataSource('pipelineConfigs').loading).toBe(true);
      $scope.$digest();
      expect(application.getDataSource('pipelineConfigs').loaded).toBe(true);
      expect(application.getDataSource('pipelineConfigs').loading).toBe(false);
      expect(application.getDataSource('pipelineConfigs').loadFailure).toBe(false);
    });

    it('sets appropriate flags when pipeline config reload fails; subscriber is responsible for error checking', function () {
      spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
      spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.reject([]));
      let errorsHandled = 0,
          successesHandled = 0;
      configureApplication();
      application.getDataSource('pipelineConfigs').activate();

      application.getDataSource('pipelineConfigs').onRefresh($scope, () => successesHandled++, () => errorsHandled++);
      application.getDataSource('pipelineConfigs').refresh();
      $scope.$digest();

      expect(application.getDataSource('pipelineConfigs').loading).toBe(false);
      expect(application.getDataSource('pipelineConfigs').loadFailure).toBe(true);

      application.getDataSource('pipelineConfigs').refresh();
      $scope.$digest();

      expect(errorsHandled).toBe(2);
      expect(successesHandled).toBe(0);
    });
  });
});
