export function arePropsEqual<T>(propsA: T, propsB: T, keys: Array<keyof T>, isEqual = (a: any, b: any) => a === b) {
  return keys.every((key) => isEqual(propsA[key], propsB[key]));
}
