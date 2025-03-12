import { mountWithState, mountWithStore } from 'enzyme-redux';
import * as Actions from 'kayenta/actions';
import * as React from 'react';
import { connect } from 'react-redux';
import Select, { Option, ReactSelectProps } from 'react-select';
import { createMockStore } from 'redux-test-utils';

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
    const component = mountWithState(<Component value="" onChange={noop} />, state);
    const allProps: any = component.find(Select).first().props();

    expect(allProps.options.map((o: Option) => o.value)).toEqual([
      'datadog.agent.running',
      'datadog.trace_agent.heartbeat',
    ]);
  });

  it('queries for metric descriptors on input change', () => {
    const store = createMockStore(state);
    const component = mountWithStore(<Component value="" onChange={noop} />, store);

    const allProps: ReactSelectProps = component.find(Select).props();
    allProps.onInputChange('heartbeat');

    expect(
      store.isActionDispatched({
        type: Actions.UPDATE_DATADOG_METRIC_DESCRIPTOR_FILTER,
        payload: { filter: 'heartbeat' },
      }),
    ).toEqual(true);
  });
});
