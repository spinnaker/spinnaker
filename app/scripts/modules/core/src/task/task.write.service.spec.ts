import { mock, IHttpBackendService, ITimeoutService } from 'angular';

import { API_SERVICE, Api } from 'core/api/api.service';
import { TASK_WRITE_SERVICE, TaskWriter } from './task.write.service';

describe('Service: taskWriter', () => {
  let taskWriter: TaskWriter;
  let $httpBackend: IHttpBackendService;
  let timeout: ITimeoutService;
  let API: Api;

  beforeEach(mock.module(TASK_WRITE_SERVICE, API_SERVICE));

  beforeEach(
    mock.inject(
      (_taskWriter_: TaskWriter, _$httpBackend_: IHttpBackendService, _$timeout_: ITimeoutService, _API_: Api) => {
        taskWriter = _taskWriter_;
        $httpBackend = _$httpBackend_;
        timeout = _$timeout_;
        API = _API_;
      },
    ),
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

      taskWriter.cancelTask(application, taskId).then(() => (completed = true));
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

  describe('deleting task', () => {
    it('should wait until task is gone, then resolve', () => {
      const taskId = 'abc';
      const deleteUrl = [API.baseUrl, 'tasks', taskId].join('/');
      const checkUrl = [API.baseUrl, 'tasks', taskId].join('/');
      let completed = false;

      $httpBackend.expectDELETE(deleteUrl).respond(200, []);

      taskWriter.deleteTask(taskId).then(() => (completed = true));

      // first check: task is still present
      $httpBackend.expectGET(checkUrl).respond(200, [{ id: taskId }]);
      $httpBackend.flush();
      expect(completed).toBe(false);

      // second check: task retrieval returns some error, try again
      $httpBackend.expectGET(checkUrl).respond(500, null);
      timeout.flush();
      $httpBackend.flush();
      expect(completed).toBe(false);

      // third check: task is not present, should complete
      $httpBackend.expectGET(checkUrl).respond(404, null);
      timeout.flush();
      $httpBackend.flush();
      expect(completed).toBe(true);
    });
  });
});
