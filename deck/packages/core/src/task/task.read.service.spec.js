'use strict';
import { mockHttpClient } from '../api/mock/jasmine';
import { TaskReader } from './task.read.service';

describe('Service: taskReader', function () {
  let runNextPoll;
  let cancelPoll;

  beforeEach(() => {
    const nativeSetTimeout = window.setTimeout;
    const nativeClearTimeout = window.clearTimeout;
    const pollCallbacks = [];
    const pollsByHandle = new Map();
    let nextHandle = -1;
    spyOn(window, 'setTimeout').and.callFake((callback, delay, ...args) => {
      if (delay !== 1000) {
        return nativeSetTimeout.call(window, callback, delay, ...args);
      }
      const poll = { callback, cancelled: false, handle: nextHandle-- };
      pollCallbacks.push(poll);
      pollsByHandle.set(poll.handle, poll);
      return poll.handle;
    });
    cancelPoll = jasmine.createSpy('cancelPoll');
    spyOn(window, 'clearTimeout').and.callFake((handle) => {
      const poll = pollsByHandle.get(handle);
      if (poll) {
        poll.cancelled = true;
        cancelPoll(handle);
      } else {
        nativeClearTimeout.call(window, handle);
      }
    });
    runNextPoll = () => {
      const poll = pollCallbacks.shift();
      if (!poll || poll.cancelled) {
        throw new Error('No pending task poll');
      }
      poll.callback();
    };
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

    it('cancelPolling disposes the timer when polling reaches a terminal state', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 1, status: 'RUNNING' });
      const completed = TaskReader.waitUntilTaskCompletes(task);
      const pendingPoll = task.poller;
      http.expectGET('/tasks/1').respond(200, { id: 1, status: 'SUCCEEDED' });

      runNextPoll();
      await http.flush();
      await completed;

      expect(cancelPoll).toHaveBeenCalledOnceWith(pendingPoll);
    });

    it('cancelPolling stops a pending task poll', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 1, status: 'RUNNING' });
      cancelPoll.calls.reset();
      TaskReader.waitUntilTaskCompletes(task);
      const pendingPoll = task.poller;

      expect(TaskReader.cancelPolling).toEqual(jasmine.any(Function));
      if (!TaskReader.cancelPolling) {
        return;
      }

      TaskReader.cancelPolling(task);
      TaskReader.cancelPolling(task);

      expect(cancelPoll).toHaveBeenCalledOnceWith(pendingPoll);
      expect(task.poller).toBeUndefined();
      expect(runNextPoll).toThrowError('No pending task poll');
    });

    it('replaces an existing poll for the same task', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 1, status: 'RUNNING' });
      cancelPoll.calls.reset();
      TaskReader.waitUntilTaskCompletes(task);
      const firstPoll = task.poller;

      TaskReader.waitUntilTaskCompletes(task);

      expect(cancelPoll).toHaveBeenCalledOnceWith(firstPoll);
      expect(task.poller).not.toBe(firstPoll);
    });

    it('does not apply or restart an in-flight poll after cancellation', async function () {
      const http = mockHttpClient();
      const task = await getTask(http, { id: 1, status: 'RUNNING' });
      const actualRequest = http.request.bind(http);
      let notifyRequestStarted;
      const requestStarted = new Promise((resolve) => (notifyRequestStarted = resolve));
      spyOn(http, 'request').and.callFake((...args) => {
        const response = actualRequest(...args);
        notifyRequestStarted();
        return response;
      });
      TaskReader.waitUntilTaskCompletes(task);
      const pendingPoll = task.poller;
      http.expectGET('/tasks/1').respond(200, { id: 1, status: 'SUCCEEDED' });

      runNextPoll();
      await requestStarted;
      TaskReader.cancelPolling(task);
      await http.flush();

      expect(cancelPoll).toHaveBeenCalledOnceWith(pendingPoll);
      expect(task.status).toBe('RUNNING');
      expect(task.poller).toBeUndefined();
      expect(runNextPoll).toThrowError('No pending task poll');
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
