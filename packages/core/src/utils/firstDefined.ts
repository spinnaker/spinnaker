import { isNil } from 'lodash';

/** returns the first value that is neither null nor undefined */
export function firstDefined<T>(...values: T[]): T {
  return values.find((val) => !isNil(val));
}
