import { groupConsecutiveNumbers, groupDays, groupHours, GroupRange } from './AllowedTimes';

describe('days and hours grouping', () => {
  it('test grouping', () => {
    const tests: Array<{ input: number[]; expected: GroupRange[] }> = [
      { input: [1, 2, 3], expected: [{ start: 1, end: 3 }] },
      { input: [1], expected: [{ start: 1, end: 1 }] },
      {
        input: [1, 2, 3, 6, 10, 11, 12],
        expected: [
          { start: 1, end: 3 },
          { start: 6, end: 6 },
          { start: 10, end: 12 },
        ],
      },
    ];
    for (const { input, expected } of tests) {
      expect(groupConsecutiveNumbers(input)).toEqual(expected);
    }
  });

  it('test hours grouping', () => {
    const tests: Array<{ input: number[]; expected: string[] }> = [
      { input: [1, 2, 3], expected: ['01:00-04:00'] },
      { input: [1], expected: ['01:00-02:00'] },
      {
        input: [1, 2, 3, 10, 11, 12],
        expected: ['01:00-04:00', '10:00-13:00'],
      },
      { input: [22, 23, 24], expected: ['22:00-01:00'] },
    ];
    for (const { input, expected } of tests) {
      expect(groupHours(input)).toEqual(expected);
    }
  });
  it('test days grouping', () => {
    const tests: Array<{ input: number[]; expected: string[] }> = [
      { input: [1, 2, 3], expected: ['Mon-Wed'] },
      { input: [1], expected: ['Mon'] },
      {
        input: [1, 2, 3, 5, 6],
        expected: ['Mon-Wed', 'Fri-Sat'],
      },
    ];
    for (const { input, expected } of tests) {
      expect(groupDays(input)).toEqual(expected);
    }
  });
});
