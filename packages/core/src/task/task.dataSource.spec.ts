import { mock, IQService } from 'angular';

import { Application } from '../application/application.model';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { TaskReader } from './task.read.service';

describe('Task Data Source', function () {
  let application: Application, $scope: any, $q: IQService;

  beforeEach(() => ApplicationDataSourceRegistry.clearDataSources());

  beforeEach(mock.module(require('./task.dataSource').name));

  beforeEach(
    mock.inject(function (_$q_: any, $rootScope: any) {
      $q = _$q_;
      $scope = $rootScope.$new();
    }),
  );

  function configureApplication() {
    ApplicationDataSourceRegistry.registerDataSource({ key: 'serverGroups', defaultData: [] });
    application = ApplicationModelBuilder.createApplicationForTests(
      'app',
      ...ApplicationDataSourceRegistry.getDataSources(),
    );
    application.refresh().catch(() => {});
    application.getDataSource('tasks').activate();
    $scope.$digest();
  }

  describe('loading tasks', function () {
    beforeEach(function () {
      spyOn(TaskReader, 'getRunningTasks').and.returnValue($q.when([]));
    });

    it('loads tasks and sets appropriate flags', function () {
      spyOn(TaskReader, 'getTasks').and.returnValue($q.when([]));
      configureApplication();
      expect(application.getDataSource('tasks').loaded).toBe(true);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(false);
    });

    it('sets appropriate flags when task load fails', function () {
      spyOn(TaskReader, 'getTasks').and.callFake(() => $q.reject(null));
      configureApplication();
      expect(application.getDataSource('tasks').loaded).toBe(false);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(true);
    });
  });

  describe('reload tasks', function () {
    beforeEach(function () {
      spyOn(TaskReader, 'getRunningTasks').and.returnValue($q.when([]));
    });

    it('reloads tasks and sets appropriate flags', function () {
      let nextCalls = 0;
      spyOn(TaskReader, 'getTasks').and.returnValue($q.when([]));
      configureApplication();
      application.getDataSource('tasks').onRefresh($scope, () => nextCalls++);
      expect(application.getDataSource('tasks').loaded).toBe(true);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(false);

      application.getDataSource('tasks').refresh();
      expect(application.getDataSource('tasks').loading).toBe(true);

      $scope.$digest();
      expect(application.getDataSource('tasks').loaded).toBe(true);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(false);

      expect(nextCalls).toBe(1);
    });

    it('sets appropriate flags when task reload fails; subscriber is responsible for error checking', function () {
      spyOn(TaskReader, 'getTasks').and.callFake(() => $q.reject(null));
      let errorsHandled = 0,
        successesHandled = 0;
      configureApplication();
      application.getDataSource('tasks').onRefresh(
        $scope,
        () => successesHandled++,
        () => errorsHandled++,
      );

      application.getDataSource('tasks').refresh();
      $scope.$digest();

      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(true);

      application.getDataSource('tasks').refresh();
      $scope.$digest();

      expect(errorsHandled).toBe(2);
      expect(successesHandled).toBe(0);
    });
  });
});
