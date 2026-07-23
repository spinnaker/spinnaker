import { shallow } from 'enzyme';
import React from 'react';

import { ExecutionGroupsComponent } from './ExecutionGroups';
import { ExecutionState } from '../../../state';

describe('ExecutionGroups', () => {
  const group = { executions: [{ id: 'execution-id' }], heading: 'Pipeline', runningExecutions: [] } as any;
  const application = {
    executions: { onRefresh: jasmine.createSpy('onRefresh').and.returnValue(() => undefined) },
  } as any;
  let previousFilterModel: any;

  beforeEach(() => {
    previousFilterModel = ExecutionState.filterModel;
    ExecutionState.filterModel = { asFilterModel: { groups: [group] } } as any;
  });

  afterEach(() => {
    ExecutionState.filterModel = previousFilterModel;
  });

  it('shows details from injected route state when the execution is present', () => {
    const component = shallow(
      <ExecutionGroupsComponent
        {...({
          router: { transitionService: { onSuccess: () => () => undefined } },
          stateParams: { executionId: 'execution-id' },
          stateService: { includes: () => true },
        } as any)}
        application={application}
      />,
    );

    expect(component.find('.executions').hasClass('showing-details')).toBe(true);
    component.unmount();
  });

  it('observes route changes through the injected router', () => {
    const injectedUnsubscribe = jasmine.createSpy('injectedUnsubscribe');
    const injectedOnSuccess = jasmine.createSpy('injectedOnSuccess').and.returnValue(injectedUnsubscribe);
    const component = shallow(
      <ExecutionGroupsComponent
        {...({
          router: { transitionService: { onSuccess: injectedOnSuccess } },
          stateParams: {},
          stateService: { includes: () => false },
        } as any)}
        application={application}
      />,
    );

    component.unmount();

    expect(injectedOnSuccess).toHaveBeenCalledWith({}, jasmine.any(Function));
    expect(injectedUnsubscribe).toHaveBeenCalled();
  });

  it('marks details as shown from transition target params', () => {
    let transitionSuccess: (transition: any) => void;
    const component = shallow(
      <ExecutionGroupsComponent
        {...({
          router: {
            transitionService: {
              onSuccess: (_criteria: any, callback: (transition: any) => void) => {
                transitionSuccess = callback;
                return () => undefined;
              },
            },
          },
          stateParams: {},
          stateService: { includes: () => true },
        } as any)}
        application={application}
      />,
    );

    transitionSuccess({
      from: () => ({}),
      params: () => ({ executionId: 'execution-id' }),
      to: () => ({}),
    });

    expect(component.find('.executions').hasClass('showing-details')).toBe(true);
    component.unmount();
  });
});
