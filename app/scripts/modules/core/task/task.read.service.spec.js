'use strict';

describe('Service: taskReader', function () {

  var service, $http, scope, timeout, task, API;

  beforeEach(
    window.module(
      require('./task.read.service'),
      require('../api/api.service')
    )
  );


  beforeEach(window.inject(function (taskReader, $httpBackend, $rootScope, $timeout, _API_) {
    API = _API_;
    service = taskReader;
    $http = $httpBackend;
    timeout = $timeout;
    scope = $rootScope.$new();
  }));

  beforeEach(function () {
    $http.verifyNoOutstandingExpectation();
    $http.verifyNoOutstandingRequest();
  });

  describe('waitUntilTaskMatches', function () {

    function cycle() {
      timeout.flush();
      $http.flush();
    }

    beforeEach(function () {
      service.getTask('deck', 1).then((result) => task = result);
    });

    it('resolves immediately if task already matches', function () {

      $http.whenGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, {
        id: 1,
        foo: 3,
        status: 'SUCCEEDED'
      });

      var completed = false;

      $http.flush();

      service.waitUntilTaskMatches('deck', task, (task) => task.foo === 3).then(() => completed = true);
      scope.$digest();

      expect(completed).toBe(true);
    });


    it('fails immediate if failure closure provided and task matches it', function () {

      $http.whenGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, {
        id: 1,
        foo: 3,
        status: 'SUCCEEDED'
      });

      var completed = false,
          failed = false;

      $http.flush();

      service.waitUntilTaskMatches('deck', task,
        (task) => task.foo === 4,
        (task) => task.foo === 3)
        .then(
        () => completed = true,
        () => failed = true);
      scope.$digest();

      expect(completed).toBe(false);
      expect(failed).toBe(true);
    });

    it('polls task and resolves when it matches', function () {
      $http.expectGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, { id: 1, status: 'RUNNING' });

      var completed = false,
          failed = false;

      $http.flush();

      service.waitUntilTaskMatches('deck', task,
        (task) => task.isCompleted,
        (task) => task.isFailed)
        .then(
        () => completed = true,
        () => failed = true);
      scope.$digest();

      // still running
      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // still running
      $http.expectGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, { id: 1, status: 'RUNNING' });
      cycle();
      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // succeeds
      $http.expectGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, { id: 1, status: 'SUCCEEDED' });
      cycle();
      expect(completed).toBe(true);
      expect(failed).toBe(false);

    });

    it('polls task and rejects when it matches failure closure', function () {
      $http.expectGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, { id: 1, status: 'RUNNING' });

      var completed = false,
          failed = false;

      $http.flush();

      service.waitUntilTaskMatches('deck', task,
        (task) => task.isCompleted,
        (task) => task.isFailed)
        .then(
        () => completed = true,
        () => failed = true);
      scope.$digest();

      // still running
      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // still running
      $http.expectGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, { id: 1, status: 'RUNNING' });
      cycle();
      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // succeeds
      $http.expectGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, { id: 1, status: 'TERMINAL' });
      cycle();
      expect(completed).toBe(false);
      expect(failed).toBe(true);

    });

    it('polls task and rejects if task is not returned from getTask call', function () {
      $http.expectGET(API.baseUrl + '/applications/deck/tasks/1').respond(500, {});

      var completed = false,
          failed = false;

      $http.flush();

      service.waitUntilTaskMatches('deck', task,
        (task) => task.isCompleted,
        (task) => task.isFailed)
        .then(
        () => completed = true,
        () => failed = true);
      scope.$digest();

      expect(completed).toBe(false);
      expect(failed).toBe(true);
    });
  });

  describe('task running time', function () {

    function execute() {
      service.getTask('deck', 1).then(function (resolved) { task = resolved; });

      $http.flush();
      scope.$digest();
    }

    it('uses start time to calculate running time if endTime is zero', function () {
      $http.whenGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'SUCCEEDED',
        startTime: new Date(),
        endTime: 0
      });

      execute();

      expect(task.runningTime).toBe('a few seconds');
    });

    it('uses start time to calculate running time if endTime is not present', function () {
      $http.whenGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'SUCCEEDED',
        startTime: new Date()
      });

      execute();

      expect(task.runningTime).toBe('a few seconds');
    });

    it('calculates running time based on start and end times', function () {
      var start = new Date().getTime(),
          end = start + 120 * 1000;
      $http.whenGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'SUCCEEDED',
        startTime: start,
        endTime: end
      });

      execute();

      expect(task.runningTime).toBe('2 minutes');
    });

    it('handles offset between server and client by taking the max value of current time and start time', function () {
      let now = new Date().getTime(),
          offset = 200000;
      $http.whenGET(API.baseUrl + '/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'SUCCEEDED',
        startTime: now + offset
      });

      execute();

      expect(task.runningTimeInMs).toBe(0);
    });
  });

});
