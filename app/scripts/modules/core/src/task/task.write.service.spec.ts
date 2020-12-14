import { mockHttpClient } from 'core/api/mock/jasmine';
import { mock, IHttpBackendService, ITimeoutService } from 'angular';

import { API } from 'core/api/ApiService';
import { TaskWriter } from './task.write.service';

describe('Service: TaskWriter', () => {
  let $httpBackend: IHttpBackendService;
  let timeout: ITimeoutService;

  beforeEach(
    mock.inject((_$httpBackend_: IHttpBackendService, _$timeout_: ITimeoutService) => {
      $httpBackend = _$httpBackend_;
      timeout = _$timeout_;
    }),
  );

  afterEach(() => {
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
  });

  describe('cancelling task', () => {
    it('should wait until task is canceled, then resolve', async () => {
      const http = mockHttpClient();
      const taskId = 'abc';
      const cancelUrl = [API.baseUrl, 'tasks', taskId, 'cancel'].join('/');
      const checkUrl = [API.baseUrl, 'tasks', taskId].join('/');
      let completed = false;

      http.expectPUT(cancelUrl).respond(200, []);
      http.expectGET(checkUrl).respond(200, { id: taskId });

      TaskWriter.cancelTask(taskId).then(() => (completed = true));
      await http.flush();
      expect(completed).toBe(false);

      http.expectGET(checkUrl).respond(200, { id: taskId });
      timeout.flush();
      await http.flush();

      http.expectGET(checkUrl).respond(200, { status: 'CANCELED' });
      timeout.flush();
      await http.flush();
      expect(completed).toBe(true);
    });
  });
});
