import { traverseObject } from './traverseObject';
import { set } from 'lodash';

export function filterObjectValues<T extends object>(
  object: T,
  predicate: (value: any, path: string) => boolean,
  options = {
    leafNodesOnly: true,
  },
): T {
  const result = {};

  traverseObject(
    object,
    (path, value) => {
      if (predicate(value, path)) {
        set(result, path, value);
      }
    },
    options.leafNodesOnly,
  );

  return result as T;
}
