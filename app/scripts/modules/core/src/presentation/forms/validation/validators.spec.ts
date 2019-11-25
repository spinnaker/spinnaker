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
