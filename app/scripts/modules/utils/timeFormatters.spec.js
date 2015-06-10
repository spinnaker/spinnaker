'use strict';

describe('Filter: timeFormatters', function() {
  var $filter;

  beforeEach(
    module('spinnaker.utils.timeFormatters')
  );

  describe('timePickerTime', function() {

    var filter;
    beforeEach(
      inject(
        function($filter) {
          filter = $filter('timePickerTime');
        }
      )
    );

    it('returns nothing when invalid values are provided', function() {
      expect(filter()).toBe('-');
      expect(filter({})).toBe('-');
      expect(filter({invalidField: 2})).toBe('-');
      expect(filter({h: 2})).toBe('-');
      expect(filter({h: 2, m: 1})).toBe('-');
      expect(filter({hours: 2, m: 1})).toBe('-');
      expect(filter({h: 2, minutes: 1})).toBe('-');
      expect(filter({hours: 'pasta', minutes: 1})).toBe('-');
      expect(filter({hours: 11, minutes: 'copy'})).toBe('-');
    });

    it('handles string inputs', function() {
      expect(filter({hours: '10', minutes: '30'})).toBe('10:30');
      expect(filter({hours: '10', minutes: 30})).toBe('10:30');
      expect(filter({hours: 10, minutes: '30'})).toBe('10:30');
    });

    it('prefixes hours, minutes with zeros if necessary', function() {
      expect(filter({hours: 1, minutes: 30})).toBe('01:30');
      expect(filter({hours: 10, minutes: 5})).toBe('10:05');

    });
  });

});
