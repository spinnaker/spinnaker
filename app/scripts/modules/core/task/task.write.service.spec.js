'use strict';

describe('Service: taskWriter', function () {

  var taskWriter;
  var $httpBackend;
  var timeout;

  beforeEach(
    window.module(
      require('./task.write.service')
    )
  );

  beforeEach(
    window.inject(function (_taskWriter_, _$httpBackend_, _$timeout_) {
      taskWriter = _taskWriter_;
      $httpBackend = _$httpBackend_;
      timeout = _$timeout_;
    })
  );

  afterEach(function () {
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
  });

  describe('cancelling task', function () {
    it('should wait until task is canceled, then resolve', function () {
      let completed = false;
      let taskId = 'abc';
      let cancelUrl = ['', 'applications', 'deck', 'tasks', taskId, 'cancel'].join('/');
      let checkUrl = ['', 'applications', 'deck', 'tasks', taskId].join('/');
      let application = 'deck';

      $httpBackend.expectPUT(cancelUrl).respond(200, []);
      $httpBackend.expectGET(checkUrl).respond(200, {id: taskId});

      taskWriter.cancelTask(application, taskId).then(() => completed = true);
      $httpBackend.flush();
      expect(completed).toBe(false);

      $httpBackend.expectGET(checkUrl).respond(200, {id: taskId});
      timeout.flush();
      $httpBackend.flush();

      $httpBackend.expectGET(checkUrl).respond(200, {status: 'CANCELED' });
      timeout.flush();
      $httpBackend.flush();
      expect(completed).toBe(true);
    });
  });

  describe('deleting task', function () {
    it('should wait until task is gone, then resolve', function () {
      let completed = false;
      let taskId = 'abc';
      let deleteUrl = ['', 'tasks', taskId].join('/');
      let checkUrl = ['', 'tasks', taskId].join('/');

      $httpBackend.expectDELETE(deleteUrl).respond(200, []);

      taskWriter.deleteTask(taskId).then(() => completed = true);

      // first check: task is still present
      $httpBackend.expectGET(checkUrl).respond(200, [{id: taskId}]);
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
