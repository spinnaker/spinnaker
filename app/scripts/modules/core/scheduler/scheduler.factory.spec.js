'use strict';

describe('scheduler', function() {
  const angular = require('angular');

  var $timeout;

  beforeEach(function() {
    var pollSchedule = 25;
    window.module(
      require('./scheduler.factory.js')
    );

    this.pollSchedule = pollSchedule;

    window.inject(function(schedulerFactory, _$timeout_) {
      this.scheduler = schedulerFactory.createScheduler();
      $timeout = _$timeout_;
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

    it('does not fire next repeatedly when scheduleImmediate is called within the interval window', function () {
      spyOn(this.test, 'call');
      this.scheduler.subscribe(this.test.call);
      this.scheduler.scheduleImmediate();
      this.scheduler.scheduleImmediate();
      this.scheduler.scheduleImmediate();
      this.scheduler.scheduleImmediate();
      expect(this.test.call.calls.count()).toBe(4);

      $timeout.flush();
      expect(this.test.call.calls.count()).toBe(5);

      // verify no outstanding timeouts
      expect($timeout.flush).toThrow();
    });
  });

});
