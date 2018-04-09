import { trueKeyObjectParamType, booleanParamType, inverseBooleanParamType, sortKeyParamType } from './state.provider';

describe('custom param types', () => {
  describe('trueKeyObject', () => {
    it('decodes with or without commas', () => {
      expect(trueKeyObjectParamType.decode('a,b')).toEqual({ a: true, b: true });
      expect(trueKeyObjectParamType.decode('a')).toEqual({ a: true });
    });
    it('encodes alphabetically, omitting false values', () => {
      expect(trueKeyObjectParamType.encode({ a: false, c: true, b: true })).toEqual('b,c');
    });
    it('encodes to null if no true keys found', () => {
      expect(trueKeyObjectParamType.encode({ a: false })).toBeNull();
    });
  });

  describe('boolean', () => {
    it('encodes to null if false', () => {
      expect(booleanParamType.encode(false)).toBeNull();
    });
    it('encodes to true as a string for true', () => {
      expect(booleanParamType.encode(true)).toBe('true');
    });
    it('decodes to true as a string when decoding true', () => {
      expect(booleanParamType.decode('true')).toBe(true);
    });
  });

  describe('inverse boolean', () => {
    it('only encodes if false', () => {
      expect(inverseBooleanParamType.encode(false)).toBe('true');
      expect(inverseBooleanParamType.encode(true)).toBeNull();
    });
    it('decodes to false if true', () => {
      expect(inverseBooleanParamType.decode('true')).toBe(false);
    });
  });

  describe('sortKey', () => {
    it('encodes key', () => {
      expect(sortKeyParamType.encode({ key: 'a' })).toBe('a');
    });
    it('decodes to key object', () => {
      expect(sortKeyParamType.decode('-a')).toEqual({ key: '-a' });
    });
  });
});
