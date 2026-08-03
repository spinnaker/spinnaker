import { noop } from 'lodash';

import { mockHttpClient } from '../api/mock/jasmine';
import type { MockHttpClient } from '../api/mock/mockHttpClient';
import { Application } from '../application/application.model';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { DeckRuntime } from '../bootstrap/DeckRuntime';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import type {
  IServerGroupCommand,
  IServerGroupCommandViewState,
} from './configure/common/serverGroupCommandBuilder.service';
import { ServerGroupWriter } from './serverGroupWriter.service';
import type { ITaskCommand } from '../task/taskExecutor';

interface IApplicationTask {
  refresh: () => void;
}

class TestApplication extends Application {
  public tasks: IApplicationTask;
}

describe('serverGroupWriter', function () {
  let serverGroupTransformer: any, serverGroupWriter: ServerGroupWriter;

  beforeEach(function () {
    serverGroupTransformer = {
      convertServerGroupCommandToDeployConfiguration: (command: any) => command,
    };
    serverGroupWriter = new ServerGroupWriter(serverGroupTransformer);
    spyOn(serverGroupTransformer, 'convertServerGroupCommandToDeployConfiguration').and.callFake((command: any) => {
      return command;
    });
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

describe('direct runtime server group writer', () => {
  let runtime: DeckRuntime;

  beforeEach(() => {
    runtime = createDeckRuntime();
  });

  afterEach(() => runtime.dispose());

  it('submits a destroy job with the runtime-owned writer', async () => {
    const http = mockHttpClient();
    let submitted: ITaskCommand = {};
    http
      .expectPOST('/tasks')
      .respond(200, { ref: '/1' })
      .onRequestReceived((request) => (submitted = request.data));
    http.expectGET('/tasks/1').respond(200, {});

    const task = runtime.services.serverGroupWriter.destroyServerGroup(
      {
        name: 'app-test-v001',
        account: 'test-account',
        region: 'us-east-1',
        provider: 'directRuntimeServerGroupWriterTest',
      } as any,
      { name: 'app' } as any,
    );
    await http.flush();
    await task;

    expect(submitted.job[0]).toEqual(
      jasmine.objectContaining({
        type: 'destroyServerGroup',
        serverGroupName: 'app-test-v001',
        credentials: 'test-account',
        region: 'us-east-1',
        cloudProvider: 'directRuntimeServerGroupWriterTest',
      }),
    );
  });
});
