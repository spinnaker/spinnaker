'use strict';

describe('Service: taskReader', function () {

  var service, $http, scope, timeout, task;

  beforeEach(
    window.module(
      require('./task.read.service')
    )
  );


  beforeEach(window.inject(function (taskReader, $httpBackend, $rootScope, $timeout) {
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

      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 1,
        foo: 3,
        status: 'COMPLETED'
      });

      var completed = false;

      $http.flush();

      service.waitUntilTaskMatches('deck', task, (task) => task.foo === 3).then(() => completed = true);
      scope.$digest();

      expect(completed).toBe(true);
    });


    it('fails immediate if failure closure provided and task matches it', function () {

      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 1,
        foo: 3,
        status: 'COMPLETED'
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
      $http.expectGET('/applications/deck/tasks/1').respond(200, { id: 1, status: 'RUNNING' });

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
      $http.expectGET('/applications/deck/tasks/1').respond(200, { id: 1, status: 'RUNNING' });
      cycle();
      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // succeeds
      $http.expectGET('/applications/deck/tasks/1').respond(200, { id: 1, status: 'COMPLETED' });
      cycle();
      expect(completed).toBe(true);
      expect(failed).toBe(false);

    });

    it('polls task and rejects when it matches failure closure', function () {
      $http.expectGET('/applications/deck/tasks/1').respond(200, { id: 1, status: 'RUNNING' });

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
      $http.expectGET('/applications/deck/tasks/1').respond(200, { id: 1, status: 'RUNNING' });
      cycle();
      expect(completed).toBe(false);
      expect(failed).toBe(false);

      // succeeds
      $http.expectGET('/applications/deck/tasks/1').respond(200, { id: 1, status: 'FAILED' });
      cycle();
      expect(completed).toBe(false);
      expect(failed).toBe(true);

    });

    it('polls task and rejects if task is not returned from getTask call', function () {
      $http.expectGET('/applications/deck/tasks/1').respond(500, {});

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

  describe('failure message', function () {
    it ('extracts exception object from variables', function () {
      let response = {
        variables: [
          {
            key: 'exception',
            value: {
              details: {
                error: 'From exception object'
              }
            }
          }
        ]
      };
      $http.expectGET('/applications/deck/tasks/1').respond(200, response);

      service.getTask('deck', 1).then((result) => task = result);
      $http.flush();

      expect(task.failureMessage).toBe('From exception object');
    });

    it('prefers "errors" to "error" and expects them to be an array in exception object', function () {
      let response = {
        variables: [
          {
            key: 'exception',
            value: {
              details: {
                errors: [
                  'error 1',
                  'error 2'
                ],
                error: 'From error'
              }
            }
          }
        ]
      };
      $http.expectGET('/applications/deck/tasks/1').respond(200, response);

      service.getTask('deck', 1).then((result) => task = result);
      $http.flush();

      expect(task.failureMessage).toBe('error 1, error 2');
    });

    it('returns "No reason provided" if an exception variable is present but has no details', function () {
      let response = {
        variables: [
          {
            key: 'exception',
            value: 'i should be an object'
          }
        ]
      };
      $http.expectGET('/applications/deck/tasks/1').respond(200, response);

      service.getTask('deck', 1).then((result) => task = result);
      $http.flush();

      expect(task.failureMessage).toBe('No reason provided');
    });

    it('falls back to extracting last orchestration message if no exception found in variables', function () {
      let response = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                history: [
                  { status: 'i am fine' },
                  { status: 'i am terrible' }
                ],
              }
            ]
          }
        ]
      };
      $http.expectGET('/applications/deck/tasks/1').respond(200, response);

      service.getTask('deck', 1).then((result) => task = result);
      $http.flush();

      expect(task.failureMessage).toBe('i am terrible');
    });

    it('prefers message from kato exception object if present', function () {
      let response = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                exception: {
                  message: 'I am the exception'
                },
                history: [
                  { status: 'i am terrible' }
                ],
              }
            ]
          }
        ]
      };
      $http.expectGET('/applications/deck/tasks/1').respond(200, response);

      service.getTask('deck', 1).then((result) => task = result);
      $http.flush();

      expect(task.failureMessage).toBe('I am the exception');
    });

    it('returns "No reason provided" if kato exception object does not have a message property', function () {
      let response = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                exception: 'I am the problem',
                history: [
                  { status: 'i am terrible' }
                ],
              }
            ]
          }
        ]
      };
      $http.expectGET('/applications/deck/tasks/1').respond(200, response);

      service.getTask('deck', 1).then((result) => task = result);
      $http.flush();

      expect(task.failureMessage).toBe('No reason provided');
    });

    it('returns "No reason provided" if no kato exception and no history', function () {
      let response = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                history: [],
              }
            ]
          }
        ]
      };
      $http.expectGET('/applications/deck/tasks/1').respond(200, response);

      service.getTask('deck', 1).then((result) => task = result);
      $http.flush();

      expect(task.failureMessage).toBe('No reason provided');
    });

    it('gets orchestration message from last kato task', function () {
      let response = {
        variables: [
          {
            key: 'kato.tasks',
            value: [
              {
                history: [
                  { status: 'i am the first' },
                ],
              },
              {
                history: [
                  { status: 'i am the second' },
                ],
              }
            ]
          }
        ]
      };
      $http.expectGET('/applications/deck/tasks/1').respond(200, response);

      service.getTask('deck', 1).then((result) => task = result);
      $http.flush();

      expect(task.failureMessage).toBe('i am the second');
    });

    it('returns false if no failure message is present', function () {
      $http.expectGET('/applications/deck/tasks/1').respond(200, { status: 'COMPLETED' });
      service.getTask('deck', 1).then((result) => task = result);
      $http.flush();

      expect(task.failureMessage).toBe(false);
    });
  });

  describe('task running time', function () {

    function execute() {
      service.getTask('deck', 1).then(function (resolved) { task = resolved; });

      $http.flush();
      scope.$digest();
    }

    it('uses start time to calculate running time if endTime is zero', function () {
      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'COMPLETED',
        startTime: new Date(),
        endTime: 0
      });

      execute();

      expect(task.runningTime).toBe('a few seconds');
    });

    it('uses start time to calculate running time if endTime is not present', function () {
      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'COMPLETED',
        startTime: new Date()
      });

      execute();

      expect(task.runningTime).toBe('a few seconds');
    });

    it('calculates running time based on start and end times', function () {
      var start = new Date().getTime(),
          end = start + 120 * 1000;
      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'COMPLETED',
        startTime: start,
        endTime: end
      });

      execute();

      expect(task.runningTime).toBe('2 minutes');
    });

    it('handles offset between server and client by taking the max value of current time and start time', function () {
      let now = new Date().getTime(),
          offset = 200000;
      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'COMPLETED',
        startTime: now + offset
      });

      execute();

      expect(task.runningTimeInMs).toBe(0);
    });
  });

});
