import { getStageFailureRoute } from './StageFailureMessage';

describe('StageFailureMessage', () => {
  it('builds failed-stage navigation from the injected state service', () => {
    const stateService = { current: { name: 'home.applications.application.pipelines.execution' } } as any;

    expect(getStageFailureRoute(stateService, 42, 7)).toEqual({
      params: { executionId: 42, stageId: 7 },
      state: 'home.applications.application.pipelines.execution',
    });
  });

  it('omits an absent parent execution id from failed-stage navigation', () => {
    const stateService = { current: { name: 'home.applications.application.pipelines.execution' } } as any;

    expect(getStageFailureRoute(stateService, undefined, 7)).toEqual({
      params: { stageId: 7 },
      state: 'home.applications.application.pipelines.execution',
    });
  });
});
