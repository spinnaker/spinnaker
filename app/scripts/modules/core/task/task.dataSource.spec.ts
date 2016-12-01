import {Application} from '../application/application.model';
import modelBuilderModule from '../application/applicationModel.builder';
import {APPLICATION_DATA_SOURCE_REGISTRY} from '../application/service/applicationDataSource.registry';
import {TASK_READ_SERVICE, TaskReader} from 'core/task/task.read.service';

describe('Task Data Source', function () {

  let application: Application,
      taskReader: TaskReader,
      $scope: any,
      applicationModelBuilder: any,
      applicationDataSourceRegistry: any,
      $q: ng.IQService;

  beforeEach(
    angular.mock.module(
      require('./task.dataSource'),
      TASK_READ_SERVICE,
      APPLICATION_DATA_SOURCE_REGISTRY,
      modelBuilderModule
    ));

  beforeEach(
    angular.mock.inject(function (_taskReader_: TaskReader, _$q_: any, $rootScope: any,
                            _applicationModelBuilder_: any, _applicationDataSourceRegistry_: any) {
      $q = _$q_;
      $scope = $rootScope.$new();
      taskReader = _taskReader_;
      applicationModelBuilder = _applicationModelBuilder_;
      applicationDataSourceRegistry = _applicationDataSourceRegistry_;
    })
  );


  function configureApplication() {
    applicationDataSourceRegistry.registerDataSource({key: 'serverGroups'});
    application = applicationModelBuilder.createApplication(applicationDataSourceRegistry.getDataSources());
    application.refresh();
    application.getDataSource('tasks').activate();
    $scope.$digest();
  }

  describe('loading tasks', function () {
    beforeEach(function () {
      spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
    });

    it('loads tasks and sets appropriate flags', function () {
      spyOn(taskReader, 'getTasks').and.returnValue($q.when([]));
      configureApplication();
      expect(application.getDataSource('tasks').loaded).toBe(true);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(false);
    });

    it('sets appropriate flags when task load fails', function () {
      spyOn(taskReader, 'getTasks').and.returnValue($q.reject(null));
      configureApplication();
      expect(application.getDataSource('tasks').loaded).toBe(false);
      expect(application.getDataSource('tasks').loading).toBe(false);
      expect(application.getDataSource('tasks').loadFailure).toBe(true);
    });
  });

  describe('reload tasks', function () {
    beforeEach(function () {
      spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
    });

    it('reloads tasks and sets appropriate flags', function () {
      let nextCalls = 0;
      spyOn(taskReader, 'getTasks').and.returnValue($q.when([]));
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
      spyOn(taskReader, 'getTasks').and.returnValue($q.reject(null));
      let errorsHandled = 0,
          successesHandled = 0;
      configureApplication();
      application.getDataSource('tasks').onRefresh($scope, () => successesHandled++, () => errorsHandled++);

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
