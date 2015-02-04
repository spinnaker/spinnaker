'use strict';

describe('Service: taskTracker', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var taskTracker;

  beforeEach(
    module('deckApp.tasks.tracker')
  );

  beforeEach(
    inject(function (_taskTracker_) {
      taskTracker = _taskTracker_;
    })
  );

  it('should instantiate the controller', function () {
    expect(taskTracker).toBeDefined();
  });
});

