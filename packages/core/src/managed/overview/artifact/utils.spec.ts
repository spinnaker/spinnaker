import { QueryConstraint } from '../types';
import { getConstraintsStatusSummary } from './utils';

describe('Constraints status summary', () => {
  it('only one type', () => {
    let constraint: QueryConstraint = { type: 'manual-judgement', status: 'PASS' };
    expect(getConstraintsStatusSummary([constraint])).toEqual({
      text: '1 passed',
      status: 'PASS',
    });

    constraint = { type: 'manual-judgement', status: 'PENDING' };
    expect(getConstraintsStatusSummary([constraint, constraint])).toEqual({
      text: '2 pending',
      status: 'PENDING',
    });
  });

  it('with one pending', () => {
    expect(
      getConstraintsStatusSummary([
        { type: 'manual-judgement', status: 'PENDING' },
        { type: 'manual-judgement', status: 'PASS' },
      ]),
    ).toEqual({
      text: '1 passed, 1 pending',
      status: 'PENDING',
    });
  });

  it('with one blocked', () => {
    expect(
      getConstraintsStatusSummary([
        { type: 'manual-judgement', status: 'BLOCKED' },
        { type: 'manual-judgement', status: 'PASS' },
      ]),
    ).toEqual({
      text: '1 passed, 1 pending',
      status: 'PENDING',
    });
  });

  it('combine pending and blocked', () => {
    expect(
      getConstraintsStatusSummary([
        { type: 'manual-judgement', status: 'PENDING' },
        { type: 'manual-judgement', status: 'BLOCKED' },
        { type: 'manual-judgement', status: 'PASS' },
      ]),
    ).toEqual({
      text: '1 passed, 2 pending',
      status: 'PENDING',
    });
  });

  it('with one overridden', () => {
    expect(
      getConstraintsStatusSummary([
        { type: 'manual-judgement', status: 'PASS' },
        { type: 'manual-judgement', status: 'FORCE_PASS' },
        { type: 'manual-judgement', status: 'PASS' },
      ]),
    ).toEqual({
      text: '2 passed, 1 overridden',
      status: 'FORCE_PASS',
    });
  });

  it('with one overridden', () => {
    expect(
      getConstraintsStatusSummary([
        { type: 'manual-judgement', status: 'PASS' },
        { type: 'manual-judgement', status: 'FORCE_PASS' },
        { type: 'manual-judgement', status: 'PENDING' },
      ]),
    ).toEqual({
      text: '1 passed, 1 overridden, 1 pending',
      status: 'PENDING',
    });
  });

  it('with one failed', () => {
    expect(
      getConstraintsStatusSummary([
        { type: 'manual-judgement', status: 'PASS' },
        { type: 'manual-judgement', status: 'FAIL' },
        { type: 'manual-judgement', status: 'PENDING' },
        { type: 'manual-judgement', status: 'FORCE_PASS' },
      ]),
    ).toEqual({
      text: '1 passed, 1 overridden, 1 pending, 1 failed',
      status: 'FAIL',
    });
  });
});
