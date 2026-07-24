import { DateTime, Settings } from 'luxon';

import { SETTINGS } from '../config/settings';
import { duration, timeDiffToString, timePickerTime, timestamp } from './timeFormatters';

describe('Filter: timeFormatters', function () {
  beforeEach(function () {
    SETTINGS.defaultTimeZone = 'Etc/GMT+0';
  });

  afterEach(SETTINGS.resetToOriginal);

  describe('timePickerTime', function () {
    describe('timePicker', function () {
      it('returns nothing when invalid values are provided', function () {
        expect(timePickerTime(undefined)).toBe('-');
        expect(timePickerTime({})).toBe('-');
        expect(timePickerTime({ invalidField: 2 })).toBe('-');
        expect(timePickerTime({ h: 2 })).toBe('-');
        expect(timePickerTime({ h: 2, m: 1 })).toBe('-');
        expect(timePickerTime({ hours: 2, m: 1 })).toBe('-');
        expect(timePickerTime({ h: 2, minutes: 1 })).toBe('-');
        expect(timePickerTime({ hours: 'pasta', minutes: 1 })).toBe('-');
        expect(timePickerTime({ hours: 11, minutes: 'copy' })).toBe('-');
      });

      it('handles string inputs', function () {
        expect(timePickerTime({ hours: '10', minutes: '30' })).toBe('10:30');
        expect(timePickerTime({ hours: '10', minutes: 30 })).toBe('10:30');
        expect(timePickerTime({ hours: 10, minutes: '30' })).toBe('10:30');
      });

      it('prefixes hours, minutes with zeros if necessary', function () {
        expect(timePickerTime({ hours: 1, minutes: 30 })).toBe('01:30');
        expect(timePickerTime({ hours: 10, minutes: 5 })).toBe('10:05');
      });
    });

    describe('timestamp', function () {
      it('returns nothing when invalid values are provided', function () {
        expect(timestamp(undefined)).toBe('-');
        expect(timestamp(null)).toBe('-');
        expect(timestamp(-1)).toBe('-');
        expect(timestamp('a')).toBe('-');
      });
      it('returns formatted date when valid value is provided', function () {
        expect(timestamp(1445707299020)).toBe('2015-10-24 17:21:39 UTC');
      });
      it('returns formatted date in user local time when valid value is provided', function () {
        SETTINGS.feature.displayTimestampsInUserLocalTime = true;
        const baseZone = Settings.defaultZoneName;
        // NOTE: this maybe breaks, depending on where the user running the test is.
        // For example, the test originally set the timezone to "Asia/Tokyo", which
        // should output "JST". However, in the US, Chrome output "GMT+9". :(
        Settings.defaultZoneName = 'Atlantic/Reykjavik';
        expect(timestamp(1445707299020)).toBe('2015-10-24 17:21:39 GMT');
        Settings.defaultZoneName = baseZone;
      });
    });

    describe('timeDiffToString', function () {
      const startTime = DateTime.fromISO('2021-02-10T16:00:00.000Z');
      it('Show only the correct units', () => {
        expect(timeDiffToString(startTime, DateTime.fromISO('2021-02-10T16:00:01.000Z'))).toBe('1s');
        expect(timeDiffToString(startTime, DateTime.fromISO('2021-02-10T16:00:21.000Z'))).toBe('21s');
        expect(timeDiffToString(startTime, DateTime.fromISO('2021-02-10T16:02:01.000Z'))).toBe('2m 1s');
        expect(timeDiffToString(startTime, DateTime.fromISO('2021-02-10T17:03:01.000Z'))).toBe('1h 3m 1s');
        expect(timeDiffToString(startTime, DateTime.fromISO('2021-02-11T17:03:01.000Z'))).toBe('1d 1h 3m 1s');
        expect(timeDiffToString(startTime, DateTime.fromISO('2021-04-11T17:03:01.000Z'))).toBe('60d 1h 3m 1s');
      });

      it('Do not skip units with zero value', () => {
        expect(timeDiffToString(startTime, DateTime.fromISO('2021-02-10T17:00:01.000Z'))).toBe('1h 0m 1s');
        expect(timeDiffToString(startTime, DateTime.fromISO('2021-02-11T16:00:00.000Z'))).toBe('1d 0h 0m 0s');
      });
    });

    describe('duration', function () {
      it('returns nothing when invalid values are provided', function () {
        expect(duration(undefined)).toBe('-');
        expect(duration(null)).toBe('-');
        expect(duration(-1)).toBe('-');
        expect(duration('a' as any)).toBe('-');
      });

      it('formats durations in ms (less than an hour) as MM:SS', function () {
        expect(duration(1000)).toBe('00:01');
        expect(duration(10000)).toBe('00:10');
        expect(duration(60000)).toBe('01:00');
        expect(duration(60000 * 59)).toBe('59:00');
      });

      it('formats durations in ms (more than an hour) as HH:MM:SS', function () {
        expect(duration(60000 * 60)).toBe('01:00:00');
        expect(duration(60000 * 60 * 10)).toBe('10:00:00');
        expect(duration(60000 * 60 * 23)).toBe('23:00:00');
      });

      it('formats durations in ms (more than an day) as D"d"HH:MM:SS', function () {
        expect(duration(60000 * 60 * 24)).toBe('1d00:00:00');
        expect(duration(60000 * 60 * 24 * 20)).toBe('20d00:00:00');
      });

      it('accurately formats number of days 31 days or higher', function () {
        expect(duration(60000 * 60 * 24 * 65)).toBe('65d00:00:00');
        expect(duration(60000 * 60 * 24 * 9999)).toBe('9999d00:00:00');
      });
    });
  });
});
