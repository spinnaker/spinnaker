'use strict';
import { AngularServices } from '../angular/services';
import { mockHttpClient } from '../api/mock/jasmine';
import { TaskReader } from './task.read.service';

describe('Service: taskReader', function () {
  let runNextPoll;

  beforeEach(() => {
    const pollCallbacks = [];
    const timeout = (callback) => {
      pollCallbacks.push(callback);
      return Promise.resolve();
    };
    runNextPoll = () => {
      const callback = pollCallbacks.shift();
      if (!callback) {
        throw new Error('No pending task poll');
      }
      callback();
    };
    spyOnProperty(AngularServices, '$timeout', 'get').and.returnValue(timeout);
  });

  async function getTask(http, taskDef) {
    http.expectGET(`/tasks/${taskDef.id}`).respond(200, taskDef);
    const promise = TaskReader.getTask(taskDef.id);
    await http.flush();
    return promise;
  }

  describe('waitUntilTaskMatches', function () {
    it('resolves immediately if task already matches', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 1, foo: 3, status: 'SUCCEEDED' });

      let completed = false;
      await TaskReader.waitUntilTaskMatches(task, (task) => task.foo === 3).then(() => (completed = true));

      expect(completed).toBe(true);
    });

    it('fails immediate if failure closure provided and task matches it', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 1, foo: 3, status: 'SUCCEEDED' });

      let completed = false,
        failed = false;

      await TaskReader.waitUntilTaskMatches(
        task,
        (task) => task.foo === 4,
        (task) => task.foo === 3,
      ).then(
        () => (completed = true),
        () => (failed = true),
      );
      expect(completed).toBe(false);
      expect(failed).toBe(true);
    });

    it('polls task and resolves when it matches', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 1, status: 'RUNNING' });

      let completed = false,
        failed = false;

      const waitForMatch = TaskReader.waitUntilTaskMatches(
        task,
        (task) => task.isCompleted,
        (task) => task.isFailed,
      ).then(
        () => (completed = true),
        () => (failed = true),
      );

      // still running
      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // still running
      http.expectGET('/tasks/1').respond(200, { id: 1, status: 'RUNNING' });
      runNextPoll();
      await http.flush();

      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // succeeds
      http.expectGET('/tasks/1').respond(200, { id: 1, status: 'SUCCEEDED' });
      runNextPoll();
      await http.flush();
      await waitForMatch;

      expect(completed).toBe(true);
      expect(failed).toBe(false);
    });

    it('polls task and rejects when it matches failure closure', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 1, status: 'RUNNING' });

      let completed = false,
        failed = false;

      const waitForMatch = TaskReader.waitUntilTaskMatches(
        task,
        (task) => task.isCompleted,
        (task) => task.isFailed,
      ).then(
        () => (completed = true),
        () => (failed = true),
      );
      // still running
      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // still running
      http.expectGET('/tasks/1').respond(200, { id: 1, status: 'RUNNING' });
      runNextPoll();
      await http.flush();
      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // succeeds
      http.expectGET('/tasks/1').respond(200, { id: 1, status: 'TERMINAL' });
      runNextPoll();
      await http.flush();
      await waitForMatch.catch(() => undefined);
      expect(completed).toBe(false);
      expect(failed).toBe(true);
    });

    it('polls task and rejects if task is not returned from getTask call', async function () {
      const http = mockHttpClient({ autoFlush: true });
      http.expectGET('/tasks/1').respond(500, {});
      const task = await TaskReader.getTask(1);

      let completed = false,
        failed = false;

      await TaskReader.waitUntilTaskMatches(
        task,
        (task) => task.isCompleted,
        (task) => task.isFailed,
      ).then(
        () => (completed = true),
        () => (failed = true),
      );
      expect(completed).toBe(false);
      expect(failed).toBe(true);
    });
  });

  describe('task running time', function () {
    it('uses start time to calculate running time if endTime is zero', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 2, status: 'SUCCEEDED', startTime: Date.now(), endTime: 0 });
      expect(task.runningTime).toBe('less than 5 seconds');
    });

    it('uses start time to calculate running time if endTime is not present', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 2, status: 'SUCCEEDED', startTime: Date.now() });
      expect(task.runningTime).toBe('less than 5 seconds');
    });

    it('calculates running time based on start and end times', async function () {
      const http = mockHttpClient();
      const start = Date.now();
      const end = start + 120 * 1000;
      const task = await getTask(http, { id: 2, status: 'SUCCEEDED', startTime: start, endTime: end });
      expect(task.runningTime).toBe('2 minutes');
    });

    it('handles offset between server and client by taking the max value of current time and start time', async function () {
      const http = mockHttpClient();
      const now = Date.now();
      const offset = 200000;
      const task = await getTask(http, { id: 2, status: 'SUCCEEDED', startTime: now + offset });
      expect(task.runningTimeInMs).toBe(0);
    });
  });
});
