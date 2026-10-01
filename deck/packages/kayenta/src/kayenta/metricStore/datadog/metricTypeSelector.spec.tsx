import { fireEvent, render, screen, within } from '@testing-library/react';
import { setupUser } from '../../../../../core/src/utils/testUtils/userEvent';
import * as Actions from '../../actions';
import * as React from 'react';
import { connect, Provider } from 'react-redux';
import { createStore } from 'redux';

import { noop } from '@spinnaker/core';

import { DatadogMetricTypeSelector, mapDispatchToProps, mapStateToProps } from './metricTypeSelector';

describe('<DatadogMetricTypeSelector />', () => {
  let Component: any;
  let state: any;

  beforeEach(() => {
    state = {
      data: {
        metricsServiceMetadata: {
          data: [
            {
              name: 'datadog.agent.running',
            },
            {
              name: 'datadog.trace_agent.heartbeat',
            },
          ],
        },
      },
    };

    Component = connect(mapStateToProps, mapDispatchToProps)(DatadogMetricTypeSelector);
  });

  it('builds options from input descriptors and selects a metric', async () => {
    const user = setupUser();
    const onChange = vi.fn();
    render(
      <Provider store={createStore(() => state)}>
        <Component value="" onChange={onChange} />
      </Provider>,
    );

    fireEvent.keyDown(screen.getByRole('combobox'), { key: 'ArrowDown', keyCode: 40 });

    const menu = within(screen.getByRole('listbox'));
    expect(menu.getByRole('option', { name: 'datadog.agent.running' })).toBeVisible();
    expect(menu.getByRole('option', { name: 'datadog.trace_agent.heartbeat' })).toBeVisible();

    await user.click(menu.getByRole('option', { name: 'datadog.trace_agent.heartbeat' }));

    expect(onChange).toHaveBeenCalledWith({
      label: 'datadog.trace_agent.heartbeat',
      value: 'datadog.trace_agent.heartbeat',
    });
  });

  it('queries for metric descriptors on input change', () => {
    const store = createStore(() => state);
    const dispatch = vi.spyOn(store, 'dispatch');
    render(
      <Provider store={store}>
        <Component value="" onChange={noop} />
      </Provider>,
    );

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'heartbeat' } });

    expect(dispatch).toHaveBeenCalledWith({
      type: Actions.UPDATE_DATADOG_METRIC_DESCRIPTOR_FILTER,
      payload: { filter: 'heartbeat' },
    });
  });
});
