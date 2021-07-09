import { mockHttpClient } from '../../api/mock/jasmine';
import { IQProvider, IQService, ITimeoutService, mock, noop } from 'angular';
import { REACT_MODULE } from '../../reactShims';

import { EXECUTION_SERVICE, ExecutionService } from './execution.service';
import { IExecution } from '../../domain';
import { Application } from '../../application';
import * as State from '../../state';

describe('Service: executionService', () => {
  let executionService: ExecutionService;
  let timeout: ITimeoutService;
  let $q: IQService;

  beforeEach(mock.module(REACT_MODULE, EXECUTION_SERVICE, 'ui.router'));

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$q
  beforeEach(
    mock.module(($qProvider: IQProvider) => {
      $qProvider.errorOnUnhandledRejections(false);
    }),
  );

  beforeEach(
    mock.inject((_executionService_: ExecutionService, _$timeout_: ITimeoutService, _$q_: IQService) => {
      executionService = _executionService_;
      timeout = _$timeout_;
      $q = _$q_;
      State.initialize();
      State.ExecutionState.filterModel.asFilterModel.sortFilter.count = 3;
    }),
  );

  describe('cancelling pipeline', () => {
    it('should wait until pipeline is not running, then resolve', async () => {
      const http = mockHttpClient();
      const executionId = 'abc';
      const application: Application = { name: 'deck', executions: { refresh: () => $q.when(null) } } as any;

      let completed = false;

      http.expectPUT(`/pipelines/${executionId}/cancel`).respond(200, []);
      http.expectGET(`/pipelines/${executionId}`).respond(200, { id: executionId, status: 'RUNNING' });

      executionService.cancelExecution(application, executionId).then(() => (completed = true));
      await http.flush();
      expect(completed).toBe(false);

      http.expectGET(`/pipelines/${executionId}`).respond(200, { id: executionId, status: 'CANCELED' });
      timeout.flush();
      await http.flush();
      expect(completed).toBe(true);
    });

    it('should propagate rejection from failed cancel', async () => {
      const http = mockHttpClient();
      let failed = false;
      const executionId = 'abc';
      const application: Application = { name: 'deck', executions: { refresh: () => $q.when(null) } } as any;

      http.expectPUT(`/pipelines/${executionId}/cancel`).respond(500, []);

      executionService.cancelExecution(application, executionId).then(noop, () => (failed = true));
      await http.flush();
      expect(failed).toBe(true);
    });
  });

  describe('deleting pipeline', () => {
    it('should wait until pipeline is missing, then resolve', async () => {
      const http = mockHttpClient();
      let completed = false;
      const executionId = 'abc';
      const application: Application = { name: 'deck', executions: { refresh: () => $q.when(null) } } as any;

      http.expectDELETE(`/pipelines/${executionId}`).respond(200, []);
      http.expectGET(`/pipelines/${executionId}`).respond(200, { id: executionId });

      executionService.deleteExecution(application, executionId).then(() => (completed = true));
      await http.flush();
      expect(completed).toBe(false);

      http.expectGET(`/pipelines/${executionId}`).respond(404, null);
      timeout.flush();
      await http.flush();
      expect(completed).toBe(true);
    });

    it('should propagate rejection from failed delete', async () => {
      const http = mockHttpClient();
      let failed = false;
      const executionId = 'abc';
      const deleteUrl = `/pipelines/${executionId}`;
      const application: Application = { name: 'deck', executions: { refresh: () => $q.when(null) } } as any;

      http.expectDELETE(deleteUrl).respond(500, []);

      executionService.deleteExecution(application, executionId).then(noop, () => (failed = true));
      await http.flush();
      expect(failed).toBe(true);
    });
  });

  describe('pausing pipeline', () => {
    it('should wait until pipeline is PAUSED, then resolve', async () => {
      const http = mockHttpClient();
      let completed = false;
      const executionId = 'abc';
      const pauseUrl = `/pipelines/${executionId}/pause`;
      const singleExecutionUrl = `/pipelines/${executionId}`;
      const application: Application = { name: 'deck', executions: { refresh: () => $q.when(null) } } as any;

      http.expectPUT(pauseUrl).respond(200, []);
      http.expectGET(singleExecutionUrl).respond(200, { id: executionId, status: 'RUNNING' });

      executionService.pauseExecution(application, executionId).then(() => (completed = true));
      await http.flush();
      expect(completed).toBe(false);

      http.expectGET(singleExecutionUrl).respond(200, { id: executionId, status: 'PAUSED' });
      timeout.flush();
      await http.flush();

      expect(completed).toBe(true);
    });
  });

  describe('resuming pipeline', () => {
    it('should wait until pipeline is RUNNING, then resolve', async () => {
      const http = mockHttpClient();
      let completed = false;
      const executionId = 'abc';
      const pauseUrl = `/pipelines/${executionId}/resume`;
      const singleExecutionUrl = `/pipelines/${executionId}`;
      const application: Application = { name: 'deck', executions: { refresh: () => $q.when(null) } } as any;

      http.expectPUT(pauseUrl).respond(200, []);
      http.expectGET(singleExecutionUrl).respond(200, { id: executionId, status: 'PAUSED' });

      executionService.resumeExecution(application, executionId).then(() => (completed = true));
      await http.flush();
      expect(completed).toBe(false);

      http.expectGET(singleExecutionUrl).respond(200, { id: executionId, status: 'RUNNING' });
      timeout.flush();
      await http.flush();

      expect(completed).toBe(true);
    });
  });

  describe('when fetching pipelines', () => {
    it('should resolve the promise if a 200 response is received with empty array', async () => {
      const http = mockHttpClient();
      http.expectGET(`/applications/deck/pipelines`).withParams({ limit: 3, expand: false }).respond(200, []);

      const responsePromise = executionService.getExecutions('deck');
      await http.flush();
      const response = await responsePromise;

      expect(response).toEqual([]);
    });

    it('should reject the promise if a 429 response is received', async () => {
      const http = mockHttpClient();
      http.expectGET(`/applications/deck/pipelines`).withParams({ limit: 3, expand: false }).respond(429, []);

      let error;
      executionService.getExecutions('deck').catch((result) => (error = result));
      await http.flush().catch(() => null);

      expect(error).toBeDefined();
    });
  });

  describe('waitUntilExecutionMatches', () => {
    it('resolves when the execution matches the closure', async () => {
      const http = mockHttpClient();
      const executionId = 'abc';
      const url = `/pipelines/${executionId}`;
      let succeeded = false;

      http.expectGET(url).respond(200, { thingToMatch: true });

      executionService
        .waitUntilExecutionMatches(executionId, (execution) => (execution as any).thingToMatch)
        .then(() => (succeeded = true));

      expect(succeeded).toBe(false);

      await http.flush();
      expect(succeeded).toBe(true);
    });

    it('polls until the execution matches, then resolves', async () => {
      const http = mockHttpClient();
      const executionId = 'abc';
      const url = `/pipelines/${executionId}`;
      let succeeded = false;

      http.expectGET(url).respond(200, { thingToMatch: false });

      executionService
        .waitUntilExecutionMatches(executionId, (execution) => (execution as any).thingToMatch)
        .then(() => (succeeded = true));

      expect(succeeded).toBe(false);

      await http.flush();
      expect(succeeded).toBe(false);

      // no match, retrying
      http.expectGET(url).respond(200, { thingToMatch: false });
      timeout.flush();
      await http.flush();

      expect(succeeded).toBe(false);

      // still no match, retrying again
      http.expectGET(url).respond(200, { thingToMatch: true });
      timeout.flush();
      await http.flush();

      expect(succeeded).toBe(true);
    });

    it('rejects if execution retrieval fails', async () => {
      const http = mockHttpClient();
      const executionId = 'abc';
      const url = `/pipelines/${executionId}`;
      let succeeded = false;
      let failed = false;

      http.expectGET(url).respond(200, { thingToMatch: false });

      executionService
        .waitUntilExecutionMatches(executionId, (execution) => (execution as any).thingToMatch)
        .then(
          () => (succeeded = true),
          () => (failed = true),
        );

      expect(succeeded).toBe(false);
      expect(failed).toBe(false);

      await http.flush();

      // no match, retrying
      expect(succeeded).toBe(false);
      expect(failed).toBe(false);
      http.expectGET(url).respond(500, '');
      timeout.flush();
      await http.flush();
      expect(succeeded).toBe(false);
      expect(failed).toBe(true);
    });
  });

  describe('merging executions and running executions', () => {
    let application: Application;
    let dataUpdated = false;
    beforeEach(() => {
      dataUpdated = false;
      application = {
        executions: { data: [], dataUpdated: () => (dataUpdated = true) },
        runningExecutions: { data: [], dataUpdated: () => (dataUpdated = true) },
      } as any;
    });

    describe('removeCompletedExecutionsFromRunningData', () => {
      it('should remove executions that have completed', () => {
        application.executions.data = [
          { id: 0, isActive: false },
          { id: 1, isActive: false },
          { id: 2, isActive: true },
        ];
        application.runningExecutions.data = [
          { id: 1, isActive: true },
          { id: 2, isActive: true },
        ];
        executionService.removeCompletedExecutionsFromRunningData(application);
        expect(application.runningExecutions.data.map((d: any) => d.id)).toEqual([2]);
        expect(dataUpdated).toBe(true);
      });
    });

    describe('mergeRunningExecutionsIntoExecutions', () => {
      it('should add running executions to executions, and update if stringVal changed', () => {
        application.executions.data = [
          { id: 0, isActive: false, stringVal: 'a' },
          { id: 2, isActive: true, stringVal: 'b' },
        ];
        application.runningExecutions.data = [
          { id: 1, isActive: true, stringVal: 'c' },
          { id: 2, isActive: true, stringVal: 'd' },
        ];
        executionService.mergeRunningExecutionsIntoExecutions(application);
        expect(application.executions.data.map((d: any) => `${d.id}:${d.stringVal}`)).toEqual(['0:a', '2:d', '1:c']);
        expect(dataUpdated).toBe(true);
      });

      it('should only call dataUpdated if actual updates occurred', () => {
        application.executions.data = [
          { id: 0, isActive: false, stringVal: 'a' },
          { id: 2, isActive: true, stringVal: 'b' },
        ];
        application.runningExecutions.data = [{ id: 2, isActive: true, stringVal: 'b' }];
        executionService.mergeRunningExecutionsIntoExecutions(application);
        expect(application.executions.data.map((d: any) => `${d.id}:${d.stringVal}`)).toEqual(['0:a', '2:b']);
        expect(dataUpdated).toBe(false);
      });
    });
  });

  describe('adding executions to applications', () => {
    let application: Application;
    beforeEach(() => {
      application = { executions: { data: [] }, runningExecutions: { data: [] } } as any;
    });
    it('should add all executions if there are none on application', () => {
      const execs: IExecution[] = [{ a: 1 }] as any;
      const data = executionService.addExecutionsToApplication(application, execs);

      expect(data).toBe(execs);
    });

    it('should add new executions', () => {
      const original = { id: 1, stringVal: 'ac' };
      const newOne = { id: 2, stringVal: 'ab' };
      const execs: IExecution[] = [original, newOne] as any;
      application.executions.data = [original];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([original, newOne]);
    });

    it('should replace an existing execution if stringVal has changed', () => {
      const originalStages = [
        { id: 'a', status: 'COMPLETED' },
        { id: 'b', status: 'RUNNING' },
        { id: 'c', status: 'RUNNING' },
        { id: 'd', status: 'NOT_STARTED' },
      ];
      const updatedStages = [
        { id: 'a', status: 'COMPLETED' },
        { id: 'b', status: 'RUNNING', newField: 'x', isActive: true },
        { id: 'c', status: 'RUNNING' },
        { id: 'd', status: 'NOT_STARTED' },
      ];
      const original = {
        id: 1,
        stringVal: 'ac',
        stageSummaries: originalStages.slice(),
        graphStatusHash: 'COMPLETED:RUNNING:RUNNING:NOT_STARTED',
        hydrated: false,
      };
      const updated = {
        id: 1,
        stringVal: 'ab',
        stageSummaries: updatedStages.slice(),
        graphStatusHash: 'COMPLETED:RUNNING:RUNNING:NOT_STARTED',
        hydrated: false,
      };
      const execs: IExecution[] = [updated] as any;
      application.executions.data = [original];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([updated]);
    });

    it('should replace an existing execution if status changes', () => {
      const original = { id: 1, stringVal: 'ac', status: 'RUNNING' };
      const updated = { id: 1, stringVal: 'ab', status: 'COMPLETED' };
      const execs: IExecution[] = [updated] as any;
      application.executions.data = [original];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([updated]);
    });

    it('should remove an execution if it is not in the new set', () => {
      const transient = { id: 1, stringVal: 'ac' };
      const persistent = { id: 2, stringVal: 'ab' };
      const execs: IExecution[] = [persistent] as any;
      application.executions.data = [transient];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([persistent]);
    });

    it('should retain running executions, even if they are not in the new set', () => {
      const running = { id: 3 };
      application.executions.data = [running];
      application.runningExecutions.data = [running];

      executionService.addExecutionsToApplication(application, []);
      expect(application.executions.data).toEqual([running]);
    });

    it('should remove multiple executions if not in the new set', () => {
      const transient1 = { id: 1, stringVal: 'ac' };
      const persistent = { id: 2, stringVal: 'ab' };
      const transient3 = { id: 3, stringVal: 'ac' };
      const execs: IExecution[] = [persistent] as any;
      application.executions.data = [transient1, persistent, transient3];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([persistent]);
    });

    it('should replace the existing executions if application has executions comes back empty', () => {
      const execs: IExecution[] = [];
      application.executions.data = [{ a: 1 }];

      const data = executionService.addExecutionsToApplication(application, execs);

      expect(data).toEqual([]);
    });
  });

  describe('waitUntilTriggeredPipelineAppears', () => {
    const applicationName = 'deck';
    const application: Application = { name: applicationName, executions: { refresh: () => $q.when(null) } } as any;
    const pipelineId = '01DC2VMFBZ5PFW5G6SMKWW5CZC';
    const url = `/pipelines/${pipelineId}`;
    const execution: any = {}; // Stub execution

    it('resolves when the pipeline exists', async () => {
      const http = mockHttpClient();
      let succeeded = false;

      http.expectGET(url).respond(200, execution);

      executionService
        .waitUntilTriggeredPipelineAppears(application, pipelineId)
        .promise.then(() => (succeeded = true));

      expect(succeeded).toBe(false);

      await http.flush();
      expect(succeeded).toBe(true);
    });

    it('does not resolve when the pipeline does not exist', async () => {
      const http = mockHttpClient();
      let succeeded = false;

      http.expectGET(url).respond(404, null);

      executionService
        .waitUntilTriggeredPipelineAppears(application, pipelineId)
        .promise.then(() => (succeeded = true));

      expect(succeeded).toBe(false);

      await http.flush();
      expect(succeeded).toBe(false);
    });

    it('resolves when the pipeline exists on a later poll', async () => {
      const http = mockHttpClient();
      let succeeded = false;

      http.expectGET(url).respond(404, null);

      executionService
        .waitUntilTriggeredPipelineAppears(application, pipelineId)
        .promise.then(() => (succeeded = true));

      expect(succeeded).toBe(false);

      await http.flush();

      expect(succeeded).toBe(false);

      // return success on the second GET request
      http.expectGET(url).respond(200, execution);
      timeout.flush();
      await http.flush();

      expect(succeeded).toBe(true);
    });
  });
});
