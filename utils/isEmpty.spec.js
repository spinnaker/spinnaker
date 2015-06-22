'use strict';

describe('isEmpty', function() {

  beforeEach(window.module('spinnaker.utils.isEmpty'));

  beforeEach(window.inject(function(_isEmpty_) {
    this.isEmpty = _isEmpty_;
  }));

  describe('isEmpty', function() {
    it('returns true for NaN, empty strings, empty arrays, null, undefined', function() {

      expect(this.isEmpty(NaN)).toBe(true);
      expect(this.isEmpty('')).toBe(true);
      expect(this.isEmpty([])).toBe(true);
      expect(this.isEmpty(null)).toBe(true);

      var x;
      expect(this.isEmpty(x)).toBe(true);
    });

    it('returns false for strings, negative numbers, positive numbers, zero, non-empty arrays', function() {
      expect(this.isEmpty('1')).toBe(false);
      expect(this.isEmpty(-1)).toBe(false);
      expect(this.isEmpty(1)).toBe(false);
      expect(this.isEmpty(0)).toBe(false);
      expect(this.isEmpty([0])).toBe(false);
    });
  });

})
.name;
