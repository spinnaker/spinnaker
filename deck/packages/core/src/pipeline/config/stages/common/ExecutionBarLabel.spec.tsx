import { ExecutionBarLabelComponent } from './ExecutionBarLabel';

describe('ExecutionBarLabel', () => {
  it('uses injected route params to include the active grouped stage name', () => {
    const component = new ExecutionBarLabelComponent({
      stage: {
        groupStages: [{ name: 'Child stage' }],
        index: 987654,
        name: 'Parent stage',
        type: 'group',
      },
      stateParams: { stage: '987654', subStage: '0' },
    } as any);

    expect((component as any).getRenderableStageName()).toBe('Parent stage: Child stage');
  });
});
