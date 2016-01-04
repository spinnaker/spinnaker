'use strict';

describe('scheduler', function() {
  const angular = require('angular');
  beforeEach(function() {
    var pollSchedule = 25;
    window.module(
      require('./scheduler.factory.js'),
      function($provide) {
        return $provide.constant('settings', {
          pollSchedule: pollSchedule,
        });
      }
    );

    this.pollSchedule = pollSchedule;

    window.inject(function(schedulerFactory) {
      this.scheduler = schedulerFactory.createScheduler();
    });

    this.test = {
      call: angular.noop,
    };
  });

  describe('#scheduleImmediate', function() {
    it('invokes all subscribed callbacks immediately', function() {
      var numSubscribers = 20;

      spyOn(this.test, 'call');
      for(var i = 0; i < numSubscribers; i++) {
        this.scheduler.subscribe(this.test.call);
      }
      var pre = this.test.call.calls.count();
      this.scheduler.scheduleImmediate();
      expect(this.test.call.calls.count() - pre).toBe(numSubscribers);
    });
  });
});
