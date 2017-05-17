import {mock, noop} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';
import {SERVER_GROUP_WRITER, ServerGroupWriter, IServerGroupCommand} from './serverGroupWriter.service';
import {ITaskCommand} from 'core/task/taskExecutor';
import {Application} from 'core/application/application.model';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from 'core/application/applicationModel.builder';
import {APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry} from 'core/application/service/applicationDataSource.registry';

interface IApplicationTask {
  refresh: () => void;
}

class TestApplication extends Application {
  public tasks: IApplicationTask;
}

describe('serverGroupWriter', function () {

  let API: Api,
    $httpBackend: ng.IHttpBackendService,
    applicationModelBuilder: ApplicationModelBuilder,
    applicationDataSourceRegistry: ApplicationDataSourceRegistry,
    serverGroupTransformer: any,
    serverGroupWriter: ServerGroupWriter;

  beforeEach(mock.module(API_SERVICE, APPLICATION_DATA_SOURCE_REGISTRY, APPLICATION_MODEL_BUILDER, SERVER_GROUP_WRITER));

  beforeEach(function () {
    mock.inject(function (_serverGroupWriter_: ServerGroupWriter,
                          _$httpBackend_: ng.IHttpBackendService,
                          _applicationModelBuilder_: ApplicationModelBuilder,
                          _applicationDataSourceRegistry_: ApplicationDataSourceRegistry,
                          _serverGroupTransformer_: any,
                          _API_: Api) {
      API = _API_;
      serverGroupWriter = _serverGroupWriter_;
      $httpBackend = _$httpBackend_;
      applicationModelBuilder = _applicationModelBuilder_;
      applicationDataSourceRegistry = _applicationDataSourceRegistry_;
      serverGroupTransformer = _serverGroupTransformer_;
    });
  });

  beforeEach(function () {
    spyOn(serverGroupTransformer, 'convertServerGroupCommandToDeployConfiguration').and.callFake((command: any) => {
      return command;
    });
  });

  it('should inject defined objects', function () {
    expect(API).toBeDefined();
    expect($httpBackend).toBeDefined();
    expect(serverGroupTransformer).toBeDefined();
    expect(serverGroupWriter).toBeDefined();
  });

  describe('clone server group submit', function () {

    function postTask(command: IServerGroupCommand): ITaskCommand {
      let submitted: ITaskCommand = {};
      $httpBackend.expectPOST(`${API.baseUrl}/applications/app/tasks`, (body: string) => {
        submitted = <ITaskCommand>JSON.parse(body);
        return true;
      }).respond(200, {ref: '/1'});

      const application: TestApplication =
        <TestApplication>applicationModelBuilder.createApplication(applicationDataSourceRegistry.getDataSources());
      application.tasks = {
        refresh: noop
      };


      $httpBackend.expectGET(API.baseUrl + '/tasks/1').respond({});
      serverGroupWriter.cloneServerGroup(command, application);
      $httpBackend.flush();

      return submitted;
    }

    let command: IServerGroupCommand;
    beforeEach(() => {

      const application: Application =
        applicationModelBuilder.createApplication(applicationDataSourceRegistry.getDataSources());
      command = {
        viewState: {
          mode: 'create',
        },
        application
      };
    });

    it('sets action type and description appropriately when creating new', function () {
      const submitted: ITaskCommand = postTask(command);
      expect(submitted.job[0].type).toBe('createServerGroup');
      expect(submitted.description).toBe('Create New Server Group in cluster app');
    });

    it('sets action type and description appropriately when creating new', function () {
      command.stack = 'main';
      const submitted: ITaskCommand = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster app-main');
    });

    it('sets action type and description appropriately when creating new', function () {
      command.stack = 'main';
      command.freeFormDetails = 'details';
      const submitted: ITaskCommand = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster app-main-details');
    });

    it('sets action type and description appropriately when creating new', function () {
      command.freeFormDetails = 'details';
      const submitted: ITaskCommand = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster app--details');
    });

    it('sets action type and description appropriately when cloning, preserving source', function () {

      command.source = {
        asgName: 'app-v002'
      };
      command.viewState = {
        mode: 'clone',
      };

      const submitted: ITaskCommand = postTask(command);
      expect(submitted.job[0].type).toBe('cloneServerGroup');
      expect(submitted.description).toBe('Create Cloned Server Group from app-v002');
      expect(submitted.job[0].source).toEqual(command.source);
    });
  });
});
