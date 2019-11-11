import { traverseObject } from './traverseObject';

describe('traverseObject', () => {
  let keyValuePairsWalked: Array<[string, any]>;
  let i: number;

  function defaultCallback(key: string, val: any) {
    keyValuePairsWalked.push([key, val]);
  }

  beforeEach(() => {
    i = 0;
    keyValuePairsWalked = [];
  });

  it('does not throw on null or undefined', () => {
    const spyCallback = jasmine.createSpy();

    expect(() => {
      traverseObject(null, spyCallback);
      traverseObject(undefined, spyCallback);
    }).not.toThrow();

    expect(spyCallback).not.toHaveBeenCalled();
  });

  it('walks simple properties of an object', () => {
    const object = { foo: 1, bar: 2 };
    traverseObject(object, defaultCallback);
    expect(keyValuePairsWalked.length).toEqual(2);
    expect(keyValuePairsWalked[i++]).toEqual(['foo', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar', 2]);
  });

  it('walks nested properties of an object', () => {
    const object = { foo: 1, bar: { prop1: 1, prop2: 2 } };
    traverseObject(object, defaultCallback);
    expect(keyValuePairsWalked.length).toEqual(4);
    expect(keyValuePairsWalked[i++]).toEqual(['foo', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar', { prop1: 1, prop2: 2 }]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar.prop1', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar.prop2', 2]);
  });

  it('walks null and undefined properties of an object', () => {
    const object = { foo: null, bar: undefined, baz: { prop1: 1, prop2: 2 } } as any;
    traverseObject(object, defaultCallback);
    expect(keyValuePairsWalked.length).toEqual(5);
    expect(keyValuePairsWalked[i++]).toEqual(['foo', null]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar', undefined]);
    expect(keyValuePairsWalked[i++]).toEqual(['baz', { prop1: 1, prop2: 2 }]);
    expect(keyValuePairsWalked[i++]).toEqual(['baz.prop1', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['baz.prop2', 2]);
  });

  it('only walks simple leaf nodes an object when traverseLeafNodesOnly is true', () => {
    const object = { foo: 1, bar: { prop1: 1, prop2: 2 } };
    traverseObject(object, defaultCallback, true);
    expect(keyValuePairsWalked.length).toEqual(3);
    expect(keyValuePairsWalked[i++]).toEqual(['foo', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar.prop1', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar.prop2', 2]);
  });

  it('walks array properties of an object', () => {
    const object = { foo: 1, bar: [1, 2] };
    traverseObject(object, defaultCallback);
    expect(keyValuePairsWalked.length).toEqual(4);
    expect(keyValuePairsWalked[i++]).toEqual(['foo', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar', [1, 2]]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[0]', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[1]', 2]);
  });

  it('walks only leaf array elements when traverseLeafNodesOnly is true', () => {
    const object = { foo: 1, bar: [1, 2] };
    traverseObject(object, defaultCallback, true);
    expect(keyValuePairsWalked.length).toEqual(3);
    expect(keyValuePairsWalked[i++]).toEqual(['foo', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[0]', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[1]', 2]);
  });

  it('walks nested objects inside array properties of an object', () => {
    const object = { foo: 1, bar: [{ name: 'abc' }, { name: 'def' }] };
    traverseObject(object, defaultCallback);
    expect(keyValuePairsWalked.length).toEqual(6);
    expect(keyValuePairsWalked[i++]).toEqual(['foo', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar', [{ name: 'abc' }, { name: 'def' }]]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[0]', { name: 'abc' }]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[0].name', 'abc']);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[1]', { name: 'def' }]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[1].name', 'def']);
  });

  it('walks only leaf nodes nested objects inside array properties of an object when traverseLeafNodesOnly is true', () => {
    const object = { foo: 1, bar: [{ name: 'abc' }, { name: 'def' }] };
    traverseObject(object, defaultCallback, true);
    expect(keyValuePairsWalked.length).toEqual(3);
    expect(keyValuePairsWalked[i++]).toEqual(['foo', 1]);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[0].name', 'abc']);
    expect(keyValuePairsWalked[i++]).toEqual(['bar[1].name', 'def']);
  });

  it('uses array syntax when a key contains a dot', () => {
    const object = { root: { 'foo.bar.baz': 1 } };
    traverseObject(object, defaultCallback);
    expect(keyValuePairsWalked.length).toEqual(2);
    expect(keyValuePairsWalked[0][0]).toBe('root');
    expect(keyValuePairsWalked[1][0]).toBe('root["foo.bar.baz"]');
  });
});
