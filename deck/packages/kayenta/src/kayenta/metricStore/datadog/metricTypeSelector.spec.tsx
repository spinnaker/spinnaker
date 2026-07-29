import { mount } from 'enzyme';
import * as Actions from '../../actions';
import * as React from 'react';
import { connect, Provider } from 'react-redux';
import Select, { Option, ReactSelectProps } from 'react-select';
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

  it('builds options from input descriptors', () => {
    const component = mount(
      <Provider store={createStore(() => state)}>
        <Component value="" onChange={noop} />
      </Provider>,
    );
    const allProps: any = component.find(Select).first().props();

    expect(allProps.options.map((o: Option) => o.value)).toEqual([
      'datadog.agent.running',
      'datadog.trace_agent.heartbeat',
    ]);
  });

  it('queries for metric descriptors on input change', () => {
    const store = createStore(() => state);
    const dispatch = spyOn(store, 'dispatch').and.callThrough();
    const component = mount(
      <Provider store={store}>
        <Component value="" onChange={noop} />
      </Provider>,
    );

    const allProps: ReactSelectProps = component.find(Select).props();
    allProps.onInputChange('heartbeat');

    expect(dispatch).toHaveBeenCalledWith({
      type: Actions.UPDATE_DATADOG_METRIC_DESCRIPTOR_FILTER,
      payload: { filter: 'heartbeat' },
    });
  });
});
