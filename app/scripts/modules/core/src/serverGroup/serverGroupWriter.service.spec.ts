import { mock, noop } from 'angular';

import { API } from 'core/api/ApiService';
import { SERVER_GROUP_WRITER, ServerGroupWriter } from './serverGroupWriter.service';
import {
  IServerGroupCommand,
  IServerGroupCommandViewState,
} from './configure/common/serverGroupCommandBuilder.service';
import { ITaskCommand } from 'core/task/taskExecutor';
import { Application } from 'core/application/application.model';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';
import { ApplicationDataSourceRegistry } from 'core/application/service/ApplicationDataSourceRegistry';

interface IApplicationTask {
  refresh: () => void;
}

class TestApplication extends Application {
  public tasks: IApplicationTask;
}

describe('serverGroupWriter', function() {
  let $httpBackend: ng.IHttpBackendService,
    applicationModelBuilder: ApplicationModelBuilder,
    serverGroupTransformer: any,
    serverGroupWriter: ServerGroupWriter;

  beforeEach(mock.module(APPLICATION_MODEL_BUILDER, SERVER_GROUP_WRITER));

  beforeEach(function() {
    mock.inject(function(
      _serverGroupWriter_: ServerGroupWriter,
      _$httpBackend_: ng.IHttpBackendService,
      _applicationModelBuilder_: ApplicationModelBuilder,
      _serverGroupTransformer_: any,
    ) {
      serverGroupWriter = _serverGroupWriter_;
      $httpBackend = _$httpBackend_;
      applicationModelBuilder = _applicationModelBuilder_;
      serverGroupTransformer = _serverGroupTransformer_;
    });
  });

  beforeEach(function() {
    spyOn(serverGroupTransformer, 'convertServerGroupCommandToDeployConfiguration').and.callFake((command: any) => {
      return command;
    });
  });

  it('should inject defined objects', function() {
    expect(API).toBeDefined();
    expect($httpBackend).toBeDefined();
    expect(serverGroupTransformer).toBeDefined();
    expect(serverGroupWriter).toBeDefined();
  });

  describe('clone server group submit', function() {
    function postTask(serverGroupCommand: IServerGroupCommand): ITaskCommand {
      let submitted: ITaskCommand = {};
      $httpBackend
        .expectPOST(`${API.baseUrl}/applications/app/tasks`, (body: string) => {
          submitted = JSON.parse(body) as ITaskCommand;
          return true;
        })
        .respond(200, { ref: '/1' });

      const application: TestApplication = applicationModelBuilder.createApplication(
        'app',
        ...ApplicationDataSourceRegistry.getDataSources(),
      ) as TestApplication;
      application.tasks = {
        refresh: noop,
      };

      $httpBackend.expectGET(API.baseUrl + '/tasks/1').respond({});
      serverGroupWriter.cloneServerGroup(serverGroupCommand, application);
      $httpBackend.flush();

      return submitted;
    }

    let command: IServerGroupCommand;
    beforeEach(() => {
      const application: Application = applicationModelBuilder.createApplication(
        'app',
        ...ApplicationDataSourceRegistry.getDataSources(),
      );
      command = {
        viewState: {
          mode: 'create',
        },
        application: application.name,
      } as IServerGroupCommand;
    });

    it('sets action type and description appropriately when creating new', function() {
      const submitted: ITaskCommand = postTask(command);
      expect(submitted.job[0].type).toBe('createServerGroup');
      expect(submitted.description).toBe('Create New Server Group in cluster app');
    });

    it('sets action type and description appropriately when creating new', function() {
      command.stack = 'main';
      const submitted: ITaskCommand = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster app-main');
    });

    it('sets action type and description appropriately when creating new', function() {
      command.stack = 'main';
      command.freeFormDetails = 'details';
      const submitted: ITaskCommand = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster app-main-details');
    });

    it('sets action type and description appropriately when creating new', function() {
      command.freeFormDetails = 'details';
      const submitted: ITaskCommand = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster app--details');
    });

    it('sets action type and description appropriately when cloning, preserving source', function() {
      command.source = {
        asgName: 'app-v002',
      };
      command.viewState = {
        mode: 'clone',
      } as IServerGroupCommandViewState;

      const submitted: ITaskCommand = postTask(command);
      expect(submitted.job[0].type).toBe('cloneServerGroup');
      expect(submitted.description).toBe('Create Cloned Server Group from app-v002');
      expect(submitted.job[0].source).toEqual(command.source);
    });
  });
});
