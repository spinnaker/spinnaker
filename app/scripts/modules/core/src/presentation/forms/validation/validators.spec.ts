import { Validators } from './validators';

describe('Target Group validators', () => {
  describe('is a number', () => {
    it('returns an error if a string is inputted', () => {
      const actual = Validators.isNum('Should be a number')('hello');
      expect(actual).toBeTruthy();
    });

    it('returns null if a number is inputted', () => {
      const actual = Validators.isNum()(5);
      expect(actual).toEqual(null);
    });

    it('Will return a default error message', () => {
      const actual = Validators.isNum()('hello');
      expect(actual).toBeTruthy();
    });
  });

  describe('isValidJson', () => {
    it('returns an error message if the string value is not valid json', () => {
      expect(Validators.isValidJson('errormessage')('the quick brown fox')).toBe('errormessage');
      expect(Validators.isValidJson('errormessage')(`{ "foo": "bar"`)).toBe('errormessage');
      expect(Validators.isValidJson('errormessage')(`{ "foo": bar }`)).toBe('errormessage');
    });

    it('returns undefined if the string value is valid json', () => {
      expect(Validators.isValidJson()(`{ "foo": "bar", "baz": 100 }`)).toBeUndefined();
      expect(Validators.isValidJson()(`{ "foo": "bar", "nest": { "number": 100 } }`)).toBeUndefined();
    });
  });

  describe('isValidXml', () => {
    it('returns an error message if the string value is not valid xml', () => {
      expect(Validators.isValidXml('errormessage')('the quick brown fox')).toBe('errormessage');
      expect(Validators.isValidXml('errormessage')(`<foo>bar<foo>`)).toBe('errormessage');
      expect(Validators.isValidXml('errormessage')(`<foo bar=123></foo>`)).toBe('errormessage');
    });

    it('returns undefined if the string value is valid xml', () => {
      expect(Validators.isValidXml()(`<foo><bar/></foo>`)).toBeUndefined();
      expect(Validators.isValidXml()(`<foo attr="123"><bar></bar></foo>`)).toBeUndefined();
    });
  });

  describe('of max/min limits', () => {
    it('returns an error when less than min', () => {
      const actual = Validators.checkBetween('field', 10, 100)('8');
      expect(actual).toBeTruthy();
    });

    it('returns an error when greater than max', () => {
      const actual = Validators.checkBetween('field', 10, 100)('125');
      expect(actual).toBeTruthy();
    });

    it('returns null when within limits', () => {
      const actual = Validators.checkBetween('field', 10, 100)('50');
      expect(actual).toEqual(null);
    });
  });
});
