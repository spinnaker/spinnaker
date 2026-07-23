import { mock } from 'angular';

import type { Application } from '../application/application.model';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { registerTaskDataSources } from './task.dataSource';
import { TaskReader } from './task.read.service';

describe('Task Data Source', function () {
  const promiseService = { when: <T>(value: T | PromiseLike<T>) => Promise.resolve(value) };
  let application: Application, $scope: any;

  beforeEach(() => ApplicationDataSourceRegistry.clearDataSources());

  beforeEach(mock.module(require('./task.dataSource').name));

  beforeEach(
    mock.inject(function (_clusterService_: any, $rootScope: any) {
      $scope = $rootScope.$new();
      ApplicationDataSourceRegistry.clearDataSources();
      registerTaskDataSources(promiseService, _clusterService_);
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
    application.refresh().catch(() => {});
    await Promise.all(refreshes);
    const tasksRefresh = waitForRefresh(application.getDataSource('tasks'));
    application.getDataSource('tasks').activate();
    await tasksRefresh;
  }

  describe('loading tasks', function () {
    beforeEach(function () {
      spyOn(TaskReader, 'getRunningTasks').and.returnValue(Promise.resolve([]));
    });

    it('loads tasks and sets appropriate flags', async function () {
      spyOn(TaskReader, 'getTasks').and.returnValue(Promise.resolve([]));
      await configureApplication();
      expect(application.getDataSource('tasks').loaded).toBe(true);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(false);
    });

    it('sets appropriate flags when task load fails', async function () {
      spyOn(TaskReader, 'getTasks').and.returnValue(Promise.reject(null));
      await configureApplication();
      expect(application.getDataSource('tasks').loaded).toBe(false);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(true);
    });
  });

  describe('reload tasks', function () {
    beforeEach(function () {
      spyOn(TaskReader, 'getRunningTasks').and.returnValue(Promise.resolve([]));
    });

    it('reloads tasks and sets appropriate flags', async function () {
      let nextCalls = 0;
      spyOn(TaskReader, 'getTasks').and.returnValue(Promise.resolve([]));
      await configureApplication();
      application.getDataSource('tasks').onRefresh($scope, () => nextCalls++);
      expect(application.getDataSource('tasks').loaded).toBe(true);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(false);

      const refreshComplete = waitForRefresh(application.getDataSource('tasks'));
      application.getDataSource('tasks').refresh();
      expect(application.getDataSource('tasks').loading).toBe(true);

      await refreshComplete;
      expect(application.getDataSource('tasks').loaded).toBe(true);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(false);

      expect(nextCalls).toBe(1);
    });

    it('sets appropriate flags when task reload fails; subscriber is responsible for error checking', async function () {
      spyOn(TaskReader, 'getTasks').and.returnValue(Promise.reject(null));
      let errorsHandled = 0;
      let successesHandled = 0;
      await configureApplication();
      application.getDataSource('tasks').onRefresh(
        $scope,
        () => successesHandled++,
        () => errorsHandled++,
      );

      await refreshDataSource(application.getDataSource('tasks'));

      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(true);

      await refreshDataSource(application.getDataSource('tasks'));

      expect(errorsHandled).toBe(2);
      expect(successesHandled).toBe(0);
    });
  });
});
