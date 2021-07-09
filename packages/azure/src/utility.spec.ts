import Utility, { ITagResult, TagError } from './utility';

describe('Azure Utility', function () {
  it('returns tag error when null object passed', function () {
    const tags: any = null;
    const result: ITagResult = Utility.checkTags(tags);
    expect(result.isValid).toBeFalsy();
    expect(result.error).toBe(TagError.TAG_OBJECT_UNDEFINED);
  });

  it('returns tag error when number of tags exceeds limitation', function () {
    const tags: any = {};
    for (let i = 0; i < 13; i++) {
      tags[`key${i}`] = `value${i}`;
    }
    const result: ITagResult = Utility.checkTags(tags);
    expect(result.isValid).toBeFalsy();
    expect(result.error).toBe(TagError.TAG_NUMBER_EXCEED);
  });

  it('returns tag error when length of key exceeds limitation', function () {
    const tags: any = {};
    tags['abcd'.repeat(128 + 1)] = '1';
    const result: ITagResult = Utility.checkTags(tags);
    expect(result.isValid).toBeFalsy();
    expect(result.error).toBe(TagError.TAG_KEY_LENGTH_EXCEED);
  });

  it('returns tag error when length of value exceeds limitation', function () {
    const tags: any = {};
    tags['abcd'] = 'abcd'.repeat(64 + 1);
    const result: ITagResult = Utility.checkTags(tags);
    expect(result.isValid).toBeFalsy();
    expect(result.error).toBe(TagError.TAG_VALUE_LENGTH_EXCEED);
  });

  it('returns tag error when key contains invalid character', function () {
    const tags: any = {};
    tags['abc&def'] = '1';
    const result: ITagResult = Utility.checkTags(tags);
    expect(result.isValid).toBeFalsy();
    expect(result.error).toBe(TagError.TAG_KEY_INVALID_CHARACTER);
  });

  it('returns tag error when value contains invalid character', function () {
    const tags: any = {};
    tags['abcd'] = 'abc?def';
    const result: ITagResult = Utility.checkTags(tags);
    expect(result.isValid).toBeFalsy();
    expect(result.error).toBe(TagError.TAG_VALUE_INVALID_CHARACTER);
  });

  it('returns no error when value are valid', function () {
    const tags: any = {};
    tags['abcd'] = 'abcdef';
    const result: ITagResult = Utility.checkTags(tags);
    expect(result.isValid).toBeTruthy();
    expect(result.error).toBeNull();
  });
});
