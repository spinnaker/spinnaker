import { RoutingState } from './RoutingState';

describe('RoutingState', () => {
  it('stays active until overlapping transitions finish', () => {
    const state = new RoutingState();
    const values: boolean[] = [];
    state.subscribe((routing) => values.push(routing));
    const finishFirst = state.begin();
    const finishSecond = state.begin();
    finishFirst();
    finishFirst();
    expect(state.routing).toBe(true);
    finishSecond();
    finishSecond();
    expect(state.routing).toBe(false);
    expect(values).toEqual([false, true, false]);
  });

  it('makes disposal and late completion idempotent', () => {
    const state = new RoutingState();
    const values: boolean[] = [];
    state.subscribe((routing) => values.push(routing));
    const finish = state.begin();
    state.dispose();
    finish();
    finish();
    state.dispose();
    expect(state.routing).toBe(false);
    expect(values).toEqual([false, true, false]);
    expect(state.begin()).toEqual(jasmine.any(Function));
  });
});
