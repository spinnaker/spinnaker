import { shallow } from 'enzyme';
import React from 'react';

import { Markdown } from '../../presentation';
import { getStageFailureRoute, StageFailureMessageComponent } from './StageFailureMessage';

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

describe('StageFailureMessageComponent', () => {
  it('renders a single failure message with the wrap-friendly class', () => {
    const component = shallow(
      <StageFailureMessageComponent {...({ stage: { isFailed: true }, message: 'boom' } as any)} />,
    )
      .dive()
      .dive();

    expect(component.find(Markdown).prop('className')).toBe('break-word-wrap');
  });

  it('renders multiple exception messages with the wrap-friendly class', () => {
    const component = shallow(
      <StageFailureMessageComponent {...({ stage: { isFailed: true }, messages: ['first', 'second'] } as any)} />,
    )
      .dive()
      .dive();

    const markdowns = component.find(Markdown);
    expect(markdowns.length).toEqual(2);
    markdowns.forEach((node) => expect(node.prop('className')).toBe('break-word-wrap'));
  });
});
