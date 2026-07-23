import { AngularServices } from '../angular/services';
import { mockHttpClient } from '../api/mock/jasmine';
import { TaskWriter } from './task.write.service';

describe('Service: TaskWriter', () => {
  let runNextPoll: () => void;

  beforeEach(() => {
    const pollCallbacks: Array<() => void> = [];
    const timeout = (callback: () => void) => {
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
    spyOnProperty(AngularServices, '$timeout', 'get').and.returnValue(timeout as any);
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
      runNextPoll();
      await http.flush();

      http.expectGET(checkUrl).respond(200, { status: 'CANCELED' });
      runNextPoll();
      await http.flush();
      await cancellation;
      expect(completed).toBe(true);
    });
  });
});
