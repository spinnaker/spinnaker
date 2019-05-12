import {
  defaultDurationObject,
  defaultDurationString,
  getDurationString,
  parseDurationString,
  IDuration,
} from './duration';

interface IDurationTest {
  str: string;
  obj: IDuration;
}

describe('Duration utils', () => {
  let validDurationStringsToObjects: IDurationTest[];
  let invalidDurationStrings: any[];
  let invalidDurationObjects: any[];

  beforeEach(() => {
    validDurationStringsToObjects = [
      {
        str: 'PT1H0M',
        obj: { hours: 1, minutes: 0 },
      },
      {
        str: 'PT0H10M',
        obj: { hours: 0, minutes: 10 },
      },
      {
        str: 'PT0H0M',
        obj: { hours: 0, minutes: 0 },
      },
      {
        str: 'PT20H6M',
        obj: { hours: 20, minutes: 6 },
      },
    ];

    invalidDurationStrings = ['invalid', { k: 'not_a_string' }, null];

    invalidDurationObjects = [null, 'not_an_object', { hours: 'not_an_int', minutes: -1 }];
  });

  describe('parseDurationString', () => {
    it('converts duration string to object', () => {
      validDurationStringsToObjects.forEach(({ str, obj }) => {
        expect(parseDurationString(str)).toEqual(obj);
      });
    });
    it('handles invalid input', () => {
      invalidDurationStrings.forEach(durationString => {
        expect(parseDurationString(durationString)).toEqual(defaultDurationObject);
      });
    });
  });

  describe('getDurationString', () => {
    it('converts duration object to string', () => {
      validDurationStringsToObjects.forEach(({ str, obj }) => {
        expect(getDurationString(obj)).toEqual(str);
      });
    });
    it('handles invalid input', () => {
      invalidDurationObjects.forEach(durationObject => {
        expect(getDurationString(durationObject)).toEqual(defaultDurationString);
      });
    });
  });
});
