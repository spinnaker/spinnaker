'use strict';
var matchers = {
  textMatch: function() {
    return {
      compare: function(actual, expected) {
        if (expected === undefined) {
          expected = '';
        }
        if (actual !== undefined) {
          actual = actual.text().trim().replace(/\s+/g, ' ');
        }
        var result = {};
        result.pass = expected === actual;
        if (result.pass) {
          result.message = 'Expected ' + expected;
        } else {
          result.message = 'Expected ' + expected + ' but was ' + actual;
        }
        return result;
      }
    };
  }
};

module.exports = matchers;
