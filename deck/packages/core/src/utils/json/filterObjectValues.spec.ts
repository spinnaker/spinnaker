import { filterObjectValues } from './filterObjectValues';

describe('filterObjectValues', () => {
  const source: any = {
    foo: {
      bar: [1, 2, 3],
      baz: {
        nest: undefined,
        nest2: null,
        nest3: 'abcdef',
      },
      fn: function () {},
    },
  };

  it('returns a copy of the object if the predicate always returns true', () => {
    const result = filterObjectValues(source, () => true);
    expect(result).toEqual(source);
  });

  it('does not populate properties which have had all their children filtered out', () => {
    const result = filterObjectValues(source, (val) => typeof val === 'string');
    const expected = {
      foo: {
        baz: {
          nest3: 'abcdef',
        },
      },
    };
    expect(result).toEqual(expected);
  });

  it('returns sparse arrays when filtered', () => {
    const result = filterObjectValues(source, (val) => val === 2);
    const expected = {
      foo: {
        bar: [undefined, 2],
      },
    };
    expect(result).toEqual(expected);
  });
});
