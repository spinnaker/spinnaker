import * as React from 'react';
import { connect } from 'react-redux';
import { mountWithState, mountWithStore } from 'enzyme-redux';
import { createMockStore } from 'redux-test-utils';
import Select from 'react-select';

import { noop } from '@spinnaker/core';
import * as Actions from 'kayenta/actions';
import { mapDispatchToProps, mapStateToProps, DatadogMetricTypeSelector } from './metricTypeSelector';

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

    Component = connect(
      mapStateToProps,
      mapDispatchToProps,
    )(DatadogMetricTypeSelector);
  });

  it('builds options from input descriptors', () => {
    const component = mountWithState(<Component value="" onChange={noop} />, state);

    expect(
      component
        .find(Select)
        .first()
        .props()
        .options.map(o => o.value),
    ).toEqual(['datadog.agent.running', 'datadog.trace_agent.heartbeat']);
  });

  it('queries for metric descriptors on input change', () => {
    const store = createMockStore(state);
    const component = mountWithStore(<Component value="" onChange={noop} />, store);

    component
      .find(Select)
      .props()
      .onInputChange('heartbeat');

    expect(
      store.isActionDispatched({
        type: Actions.UPDATE_DATADOG_METRIC_DESCRIPTOR_FILTER,
        payload: { filter: 'heartbeat' },
      }),
    ).toEqual(true);
  });
});
