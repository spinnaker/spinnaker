import { JsonUtils, IJsonDiff } from './JsonUtils';

describe('JsonUtilityService', () => {
  it('generates a nice alphabetized diff', () => {
    const left = `{"num": 3, "str": "a", "arr": ["b", "a"], "obj": {"a": 1, "c": 3, "b": 2, "arr": [1, 3, 2]}}`;
    const right = `{"num": 2, "str": "a", "arr": ["b", "c"], "obj": {"a": 1, "c": 4, "b": 2, "arr": [1, 3, 2, 4]}}`;

    const result: IJsonDiff = JsonUtils.diff(left, right, true);
    expect(result.summary).toEqual({ additions: 5, removals: 4, unchanged: 14, total: 23 });
    expect(result.changeBlocks.length).toBe(8);
    expect(result.details.length).toBe(23);
    expect(result.details.map((d) => d.text)).toEqual([
      '{',
      '  "arr": [',
      '    "b",',
      '    "a"',
      '    "c"', // added
      '  ],',
      '  "num": 3,',
      '  "num": 2,', // changed
      '  "obj": {',
      '    "a": 1,',
      '    "arr": [',
      '      1,',
      '      3,',
      '      2',
      '      2,',
      '      4', // added
      '    ],',
      '    "b": 2,',
      '    "c": 3',
      '    "c": 4', // changed
      '  },',
      '  "str": "a"',
      '}',
    ]);
  });
});
