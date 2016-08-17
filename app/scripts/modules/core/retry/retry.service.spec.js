'use strict';

describe('Service: Retry', function () {
  let angular = require('angular'), buildRetrySequence, $q, $timeout;

  beforeEach(window.module(require('./retry.service.js')));

  beforeEach(window.inject(function(retryService, _$q_, _$timeout_) {
    buildRetrySequence = retryService.buildRetrySequence;
    $q = _$q_;
    $timeout = _$timeout_;
  }));

  describe('buildRetrySequence', function () {
    it ('should only call callback once if result passes stop condition', function () {
      let callCount = 0;
      let callback = () => {
        callCount++;
        return $q.resolve(true);
      };
      let stopCondition = (val) => val;

      buildRetrySequence(callback, stopCondition, 100, 100)
        .then((result) => {
          expect(result).toEqual(true);
          expect(callCount).toEqual(1);
        });

      $timeout.flush();
    });

    it ('should return callback result and stop sequence if result passes stop condition', function () {
      let callCount = 0;
      let callback = () => $q.resolve(++callCount);
      let stopCondition = (val) => val === 8;

      buildRetrySequence(callback, stopCondition, 100, 100)
        .then((result) => {
          expect(result).toEqual(8);
          expect(callCount).toEqual(8);
        });

      $timeout.flush();
    });

    it (`should return callback result after retry limit has been met
         even if result does not pass stop condition`, function () {
      let callCount = 0;
      let callback = () => {
        callCount++;
        return $q.resolve([]);
      };
      let stopCondition = (result) => result.length > 0;

      buildRetrySequence(callback, stopCondition, 100, 100)
        .then((result) => {
          expect(result).toEqual([]);
          expect(callCount).toEqual(101);
        });

      $timeout.flush();
    });

    it (`should be tolerant of a function that does not return a promise 
        (only relevant if stopCondition is met on first try)`, function () {
      let callback = () => true;
      let stopCondition = () => true;

      expect(() => buildRetrySequence(callback, stopCondition, 100, 100)).not.toThrow();
    });
  });
});
