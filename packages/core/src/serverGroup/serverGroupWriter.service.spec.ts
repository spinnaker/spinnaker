import { mockHttpClient } from '../api/mock/jasmine';
import { mock, noop } from 'angular';
import { MockHttpClient } from '../api/mock/mockHttpClient';
import { SERVER_GROUP_WRITER, ServerGroupWriter } from './serverGroupWriter.service';
import {
  IServerGroupCommand,
  IServerGroupCommandViewState,
} from './configure/common/serverGroupCommandBuilder.service';
import { ITaskCommand } from '../task/taskExecutor';
import { Application } from '../application/application.model';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';

interface IApplicationTask {
  refresh: () => void;
}

class TestApplication extends Application {
  public tasks: IApplicationTask;
}

describe('serverGroupWriter', function () {
  let serverGroupTransformer: any, serverGroupWriter: ServerGroupWriter;

  beforeEach(mock.module(SERVER_GROUP_WRITER));

  beforeEach(function () {
    mock.inject(function (_serverGroupWriter_: ServerGroupWriter, _serverGroupTransformer_: any) {
      serverGroupWriter = _serverGroupWriter_;
      serverGroupTransformer = _serverGroupTransformer_;
    });
  });

  beforeEach(function () {
    spyOn(serverGroupTransformer, 'convertServerGroupCommandToDeployConfiguration').and.callFake((command: any) => {
      return command;
    });
  });

  it('should inject defined objects', async function () {
    expect(serverGroupTransformer).toBeDefined();
    expect(serverGroupWriter).toBeDefined();
  });

  describe('clone server group submit', function () {
    async function postTask(http: MockHttpClient, serverGroupCommand: IServerGroupCommand): Promise<ITaskCommand> {
      let submitted: ITaskCommand = {};
      http
        .expectPOST(`/tasks`)
        .respond(200, { ref: '/1' })
        .onRequestReceived((resp) => (submitted = resp.data));

      const application: TestApplication = ApplicationModelBuilder.createApplicationForTests(
        'app',
        ...ApplicationDataSourceRegistry.getDataSources(),
      ) as TestApplication;
      application.tasks = {
        refresh: noop,
      };

      http.expectGET('/tasks/1').respond(200, {});
      serverGroupWriter.cloneServerGroup(serverGroupCommand, application);
      await http.flush();

      return submitted;
    }

    let command: IServerGroupCommand;
    beforeEach(() => {
      const application: Application = ApplicationModelBuilder.createApplicationForTests(
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

    it('sets action type and description appropriately when creating new', async function () {
      const http = mockHttpClient();
      const submitted: ITaskCommand = await postTask(http, command);
      expect(submitted.job[0].type).toBe('createServerGroup');
      expect(submitted.description).toBe('Create New Server Group in cluster app');
    });

    it('sets action type and description appropriately when creating new', async function () {
      const http = mockHttpClient();
      command.stack = 'main';
      const submitted: ITaskCommand = await postTask(http, command);
      expect(submitted.description).toBe('Create New Server Group in cluster app-main');
    });

    it('sets action type and description appropriately when creating new', async function () {
      const http = mockHttpClient();
      command.stack = 'main';
      command.freeFormDetails = 'details';
      const submitted: ITaskCommand = await postTask(http, command);
      expect(submitted.description).toBe('Create New Server Group in cluster app-main-details');
    });

    it('sets action type and description appropriately when creating new', async function () {
      const http = mockHttpClient();
      command.freeFormDetails = 'details';
      const submitted: ITaskCommand = await postTask(http, command);
      expect(submitted.description).toBe('Create New Server Group in cluster app--details');
    });

    it('sets action type and description appropriately when cloning, preserving source', async function () {
      const http = mockHttpClient();
      command.source = {
        asgName: 'app-v002',
      };
      command.viewState = {
        mode: 'clone',
      } as IServerGroupCommandViewState;

      const submitted: ITaskCommand = await postTask(http, command);
      expect(submitted.job[0].type).toBe('cloneServerGroup');
      expect(submitted.description).toBe('Create Cloned Server Group from app-v002');
      expect(submitted.job[0].source).toEqual(command.source);
    });
  });
});
