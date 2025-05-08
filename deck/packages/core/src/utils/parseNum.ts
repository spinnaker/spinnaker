export const parseNum = (numOrStr: number | string): number =>
  typeof numOrStr === 'string' ? parseInt(numOrStr) : numOrStr;
