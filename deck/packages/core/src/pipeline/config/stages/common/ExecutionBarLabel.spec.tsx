import { mount } from 'enzyme';
import React from 'react';

import { ExecutionBarLabel, ExecutionBarLabelComponent } from './ExecutionBarLabel';

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

  it('renders the default tooltip without runtime context in the overlay root', () => {
    const stage = {
      id: 'stage-id',
      labelComponent: ExecutionBarLabel,
      name: 'Default stage',
      stages: [],
      suspendedStageTypes: new Set(),
      type: 'test',
    } as any;
    const wrapper = mount(
      <ExecutionBarLabelComponent
        application={{} as any}
        deckRuntimeServices={{ executionService: {} } as any}
        execution={{ hydrated: true } as any}
        executionMarker={true}
        router={{} as any}
        stage={stage}
        stateParams={{}}
        stateService={{} as any}
      >
        <span className="tooltip-trigger">marker</span>
      </ExecutionBarLabelComponent>,
    );

    try {
      expect(() => wrapper.find('.tooltip-trigger').simulate('mouseOver')).not.toThrow();
    } finally {
      wrapper.unmount();
    }
  });
});
