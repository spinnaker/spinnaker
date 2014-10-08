'use strict';

describe('Service: taskTracker', function() {
  beforeEach(function() {
    var notifications = {
      create: angular.noop,
    };

    this.initialTasks = [
      {
        id: 1,
        status: 'STARTED',
        variables: [
          {
            key: 'description',
            value: 'Resizing ASG',
          },
          {
            key: 'application',
            value: 'Hello world',
          }
        ],
      },
      {
        id: 2,
        status: 'COMPLETED',
        variables: [
          {
            key: 'description',
            value: 'Resizing ASG',
          },
          {
            key: 'application',
            value: 'Hello world',
          }
        ],
      },
    ];

    this.newTasks = [
      {
        id: 1,
        status: 'COMPLETED',
        variables: [
          {
            key: 'description',
            value: 'Resizing ASG',
          },
          {
            key: 'application',
            value: 'Hello world',
          }
        ],
      },
      {
        id: 2,
        status: 'COMPLETED',
        variables: [
          {
            key: 'description',
            value: 'Resizing ASG',
          },
          {
            key: 'application',
            value: 'Hello world',
          }
        ],
      },
    ];

    spyOn(notifications, 'create');

    module('deckApp');

    module(function($provide) {
      $provide.value('notifications', notifications);
    });

    this.notifications = notifications;
  });

  beforeEach(inject(function(taskTracker) {
    this.taskTracker = taskTracker;
  }));

  describe('getCompleted(old, new)', function() {
    it('returns a list of tasks that were not completed in old, but are in new', function() {
      var result = this.taskTracker.getCompleted(this.initialTasks, this.newTasks);
      expect(result[0]).toBe(this.newTasks[0]);
    });
  });

});
