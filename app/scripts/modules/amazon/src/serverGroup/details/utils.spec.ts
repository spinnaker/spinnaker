import { getBaseImageName } from './utils';

describe('ServerGroup details utils: getBaseImageName', () => {
  it('should accept an undefined description', () => {
    const actual = getBaseImageName();
    expect(actual).toBeUndefined();
  });

  it('should be undefined without ancestor_key in description', () => {
    const actual = getBaseImageName('thequick=brownfox, jumpsover=thelazydog');
    expect(actual).toBeUndefined();
  });

  it('should return the correct name', () => {
    const actual1 = getBaseImageName('ancestor_name=baseImageName');
    const actual2 = getBaseImageName('tricky_ancestor_name=notTheName');
    const actual3 = getBaseImageName('tricky_ancestor_name=notTheName, ancestor_name=baseImageName, another_key=hello');

    expect(actual1).toBe('baseImageName');
    expect(actual2).toBeUndefined();
    expect(actual3).toBe('baseImageName');
  });
});
