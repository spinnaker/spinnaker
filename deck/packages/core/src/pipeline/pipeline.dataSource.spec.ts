import { mock } from 'angular';

import type { Application } from '../application/application.model';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { PipelineConfigService } from './config/services/PipelineConfigService';
import { registerPipelineDataSources } from './pipeline.dataSource';
import { EXECUTION_SERVICE } from './service/execution.service';

describe('Pipeline Data Source', function () {
  const promiseService = {
    all: (promises: PromiseLike<any>[]) => Promise.all(promises),
    when: <T>(value: T | PromiseLike<T>) => Promise.resolve(value),
  };
  let application: Application, executionService: any, $scope: ng.IScope;

  beforeEach(() => ApplicationDataSourceRegistry.clearDataSources());

  beforeEach(mock.module(require('./pipeline.dataSource').name, EXECUTION_SERVICE));

  beforeEach(
    mock.inject(function (_clusterService_: any, _executionService_: any, $rootScope: ng.IRootScopeService) {
      $scope = $rootScope.$new();
      executionService = _executionService_;
      ApplicationDataSourceRegistry.clearDataSources();
      registerPipelineDataSources(promiseService, executionService, _clusterService_);
    }),
  );

  const waitForRefresh = (dataSource: any) =>
    new Promise<void>((resolve) => dataSource.onNextRefresh(null, resolve, resolve));

  async function refreshDataSource(dataSource: any) {
    const refreshComplete = waitForRefresh(dataSource);
    dataSource.refresh();
    await refreshComplete;
  }

  async function configureApplication() {
    ApplicationDataSourceRegistry.registerDataSource({ key: 'serverGroups', defaultData: [] });
    application = ApplicationModelBuilder.createApplicationForTests(
      'app',
      ...ApplicationDataSourceRegistry.getDataSources(),
    );
    const refreshes = application.dataSources.map(waitForRefresh);
    application.refresh();
    await Promise.all(refreshes);
  }

  describe('loading executions', function () {
    beforeEach(function () {
      spyOn(executionService, 'getRunningExecutions').and.returnValue(Promise.resolve([]));
      spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue(Promise.resolve([]));
      spyOn(PipelineConfigService, 'getStrategiesForApplication').and.returnValue(Promise.resolve([]));
    });

    it('loads executions and sets appropriate flags', async function () {
      spyOn(executionService, 'getExecutions').and.returnValue(Promise.resolve([{ status: 'SUCCEEDED', stages: [] }]));
      await configureApplication();
      const refreshComplete = waitForRefresh(application.getDataSource('executions'));
      application.getDataSource('executions').activate();
      await refreshComplete;
      expect(application.getDataSource('executions').loaded).toBe(true);
      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(false);
    });

    it('sets appropriate flags when execution load fails', async function () {
      spyOn(executionService, 'getExecutions').and.returnValue(Promise.reject(null));
      await configureApplication();
      const refreshComplete = waitForRefresh(application.getDataSource('executions'));
      application.getDataSource('executions').activate();
      await refreshComplete;
      expect(application.getDataSource('executions').loaded).toBe(false);
      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(true);
    });
  });

  describe('reload executions', function () {
    beforeEach(function () {
      spyOn(executionService, 'getRunningExecutions').and.returnValue(Promise.resolve([]));
      spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue(Promise.resolve([]));
      spyOn(PipelineConfigService, 'getStrategiesForApplication').and.returnValue(Promise.resolve([]));
    });

    it('reloads executions and sets appropriate flags', async function () {
      spyOn(executionService, 'getExecutions').and.returnValue(Promise.resolve([]));
      await configureApplication();
      let refreshComplete = waitForRefresh(application.getDataSource('executions'));
      application.getDataSource('executions').activate();
      await refreshComplete;
      expect(application.getDataSource('executions').loaded).toBe(true);
      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(false);

      refreshComplete = waitForRefresh(application.getDataSource('executions'));
      application.getDataSource('executions').refresh();
      expect(application.getDataSource('executions').loading).toBe(true);

      await refreshComplete;
      expect(application.getDataSource('executions').loaded).toBe(true);
      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(false);
    });

    it('sets appropriate flags when executions reload fails; subscriber is responsible for error checking', async function () {
      spyOn(executionService, 'getExecutions').and.returnValue(Promise.reject(null));
      let errorsHandled = 0;
      let successesHandled = 0;
      await configureApplication();
      const initialRefresh = waitForRefresh(application.getDataSource('executions'));
      application.getDataSource('executions').activate();
      await initialRefresh;

      application.getDataSource('executions').onRefresh(
        $scope,
        () => successesHandled++,
        () => errorsHandled++,
      );

      await refreshDataSource(application.getDataSource('executions'));

      expect(application.getDataSource('executions').loading).toBe(false);
      expect(application.getDataSource('executions').loadFailure).toBe(true);

      await refreshDataSource(application.getDataSource('executions'));

      expect(successesHandled).toBe(0);
      expect(errorsHandled).toBe(2);
    });
  });

  describe('loading pipeline configs', function () {
    beforeEach(function () {
      spyOn(executionService, 'getRunningExecutions').and.returnValue(Promise.resolve([]));
      spyOn(executionService, 'getExecutions').and.returnValue(Promise.resolve([]));
    });

    it('loads configs and sets appropriate flags', async function () {
      spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue(Promise.resolve([]));
      spyOn(PipelineConfigService, 'getStrategiesForApplication').and.returnValue(Promise.resolve([]));
      await configureApplication();
      application.getDataSource('pipelineConfigs').activate();

      const refreshComplete = waitForRefresh(application.getDataSource('pipelineConfigs'));
      application.getDataSource('pipelineConfigs').refresh();
      expect(application.getDataSource('pipelineConfigs').loading).toBe(true);
      await refreshComplete;
      expect(application.getDataSource('pipelineConfigs').loaded).toBe(true);
      expect(application.getDataSource('pipelineConfigs').loading).toBe(false);
      expect(application.getDataSource('pipelineConfigs').loadFailure).toBe(false);
    });

    it('sets appropriate flags when pipeline config reload fails; subscriber is responsible for error checking', async function () {
      spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue(Promise.resolve([]));
      spyOn(PipelineConfigService, 'getStrategiesForApplication').and.returnValue(Promise.reject([]));
      let errorsHandled = 0;
      let successesHandled = 0;
      await configureApplication();
      application.getDataSource('pipelineConfigs').activate();

      application.getDataSource('pipelineConfigs').onRefresh(
        $scope,
        () => successesHandled++,
        () => errorsHandled++,
      );
      await refreshDataSource(application.getDataSource('pipelineConfigs'));

      expect(application.getDataSource('pipelineConfigs').loading).toBe(false);
      expect(application.getDataSource('pipelineConfigs').loadFailure).toBe(true);

      await refreshDataSource(application.getDataSource('pipelineConfigs'));

      expect(errorsHandled).toBe(2);
      expect(successesHandled).toBe(0);
    });
  });
});
