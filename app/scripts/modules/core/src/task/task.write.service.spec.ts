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
    it('should wait until task is canceled, then resolve', () => {
      const taskId = 'abc';
      const cancelUrl = [API.baseUrl, 'applications', 'deck', 'tasks', taskId, 'cancel'].join('/');
      const checkUrl = [API.baseUrl, 'tasks', taskId].join('/');
      const application = 'deck';
      let completed = false;

      $httpBackend.expectPUT(cancelUrl).respond(200, []);
      $httpBackend.expectGET(checkUrl).respond(200, { id: taskId });

      TaskWriter.cancelTask(application, taskId).then(() => (completed = true));
      $httpBackend.flush();
      expect(completed).toBe(false);

      $httpBackend.expectGET(checkUrl).respond(200, { id: taskId });
      timeout.flush();
      $httpBackend.flush();

      $httpBackend.expectGET(checkUrl).respond(200, { status: 'CANCELED' });
      timeout.flush();
      $httpBackend.flush();
      expect(completed).toBe(true);
    });
  });
});
