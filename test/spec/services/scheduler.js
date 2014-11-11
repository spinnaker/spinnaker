'use strict';

describe('scheduler', function() {
  beforeEach(function() {
    var pollSchedule = 25;
    module('deckApp', function($provide) {
      return $provide.constant('settings', {
        pollSchedule: pollSchedule,
      });
    });
    this.pollSchedule = pollSchedule;

    inject(function(scheduler) {
      this.scheduler = scheduler;
    });

    this.test = {
      call: angular.noop,
    };
  });

  describe('#get', function() {
    it('returns the underlying RxSubject', function() {
      spyOn(this.test, 'call');

      this.scheduler.subscribe(this.test.call);

      expect(this.test.call.calls.count()).toEqual(0);

      this.scheduler.get().onNext();

      expect(this.test.call.calls.count()).toEqual(1);
    });
  });

  describe('subscription methods', function() {
    beforeEach(function() {
      this.numIterations = 10;
      this.cycles = 2;
    });

    describe('#subscribe', function() {
      it('it takes a callback that is invoked at each iteration of the scheduler', function(done) {

        spyOn(this.test, 'call');
        this.pre = this.test.call.calls.count();
        this.scheduler.subscribe(this.test.call);
        setTimeout(function() {
          var post = this.test.call.calls.count();
          // error bounds of +/- 1
          expect(post - this.pre).toBeGreaterThan(this.numIterations - 2);
          expect(post - this.pre).toBeLessThan(this.numIterations + 2);
          done();
        }.bind(this), this.pollSchedule * this.numIterations);

      });
    });

    describe('#subscribeEveryN', function() {
      it('it takes an int N, a callback that is invoked every N scheduler iterations', function(done) {
        spyOn(this.test, 'call');
        this.pre = this.test.call.calls.count();
        this.scheduler.subscribeEveryN(this.cycles, this.test.call);
        setTimeout(function() {
          var post = this.test.call.calls.count();
          // error bounds of +/- 1
          expect(post - this.pre).toBeGreaterThan(this.numIterations/this.cycles - 2);
          expect(post - this.pre).toBeLessThan(this.numIterations/this.cycles + 2);
          done();
        }.bind(this), this.pollSchedule * this.numIterations);
      });
    });
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
