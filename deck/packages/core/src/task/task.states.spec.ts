import { getTasksState } from './task.states';

describe('task states', () => {
  it('uses a React component for the application tasks view', () => {
    const state = getTasksState();

    expect(state.views.insight).toEqual(
      jasmine.objectContaining({
        $type: 'react',
      }),
    );
    expect(typeof state.views.insight.component).toBe('function');
    expect(state.views.insight.templateUrl).toBeUndefined();
    expect(state.views.insight.controller).toBeUndefined();
  });
});
