import { mockHttpClient } from '../api/mock/jasmine';
import { TaskWriter } from './task.write.service';

describe('Service: TaskWriter', () => {
  let cancelPoll: jasmine.Spy;
  let runNextPoll: () => number;

  beforeEach(() => {
    const nativeSetTimeout = window.setTimeout;
    const nativeClearTimeout = window.clearTimeout;
    const pollCallbacks: Array<{ callback: () => void; cancelled: boolean; handle: number }> = [];
    const pollsByHandle = new Map<number, { callback: () => void; cancelled: boolean; handle: number }>();
    let nextHandle = -1;
    spyOn(window, 'setTimeout').and.callFake((callback: TimerHandler, delay?: number, ...args: any[]) => {
      if (delay !== 1000) {
        return nativeSetTimeout.call(window, callback, delay, ...args);
      }
      if (typeof callback !== 'function') {
        throw new Error('Expected a timeout callback');
      }
      const poll = { callback, cancelled: false, handle: nextHandle-- };
      pollCallbacks.push(poll);
      pollsByHandle.set(poll.handle, poll);
      return poll.handle;
    });
    cancelPoll = jasmine.createSpy('cancelPoll');
    spyOn(window, 'clearTimeout').and.callFake((handle) => {
      const poll = pollsByHandle.get(handle as number);
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
      return poll.handle;
    };
  });

  describe('cancelling task', () => {
    it('should wait until task is canceled, then resolve', async () => {
      const http = mockHttpClient();
      const taskId = 'abc';
      const cancelUrl = `/tasks/${taskId}/cancel`;
      const checkUrl = `/tasks/${taskId}`;
      let completed = false;

      http.expectPUT(cancelUrl).respond(200, []);
      http.expectGET(checkUrl).respond(200, { id: taskId });

      const cancellation = TaskWriter.cancelTask(taskId).then(() => (completed = true));
      await http.flush();
      expect(completed).toBe(false);

      http.expectGET(checkUrl).respond(200, { id: taskId });
      const firstPoll = runNextPoll();
      await http.flush();

      http.expectGET(checkUrl).respond(200, { status: 'CANCELED' });
      const secondPoll = runNextPoll();
      await http.flush();
      await cancellation;

      expect(completed).toBe(true);
      expect(cancelPoll.calls.allArgs()).toEqual([[firstPoll], [secondPoll]]);
    });
  });
});
