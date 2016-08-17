'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.retry.service', [])
  .factory('retryService', function ($q, $timeout) {
    
    // interval is in milliseconds
    function buildRetrySequence (fn, stopCondition, limit, interval) {
      let fnCall = fn();
      let fnPromise = fnCall.then ? fnCall : $q.resolve(fnCall);

      if (limit === 0) {
        return fnPromise;
      }

      return fnPromise.then((result) => {
        if (stopCondition(result)) {
          return result;
        }
        return $timeout(interval)
          .then(() => buildRetrySequence(fn, stopCondition, limit - 1, interval));
      });
    }

    return { buildRetrySequence };
  });
