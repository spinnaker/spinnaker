import { mount } from 'enzyme';
import * as Actions from '../../actions';
import * as React from 'react';
import { connect, Provider } from 'react-redux';
import Select, { Option, ReactSelectProps } from 'react-select';
import { createStore } from 'redux';

import { noop } from '@spinnaker/core';

import { mapDispatchToProps, StackdriverMetricTypeSelector } from './metricTypeSelector';

describe('<StackdriverMetricTypeSelector />', () => {
  let Component: any;
  let state: any;

  beforeEach(() => {
    state = {
      descriptors: [
        {
          description: 'Delta count of disk read IO operations.',
          metricKind: 'DELTA',
          type: 'compute.googleapis.com/disk/read_ops_count',
          name: 'projects/my-project/metricDescriptors/compute.googleapis.com/disk/read_ops_count',
          unit: '1',
          valueType: 'INT64',
          displayName: 'Disk read operations',
        },
        {
          description: 'Delta count of throttled read operations',
          metricKind: 'DELTA',
          type: 'compute.googleapis.com/disk/throttled_read_ops_count',
          name: 'projects/my-project/metricDescriptors/compute.googleapis.com/disk/throttled_read_ops_count',
          unit: '1',
          valueType: 'INT64',
          displayName: 'Throttled read operations',
        },
      ],
      loading: false,
    };

    Component = connect(
      (s, ownProps) => ({
        ...s,
        ...ownProps,
      }),
      mapDispatchToProps,
    )(StackdriverMetricTypeSelector);
  });

  it('builds options from input descriptors', () => {
    const component = mount(
      <Provider store={createStore(() => state)}>
        <Component value="compute.googleapis.com/disk/read_ops_count" onChange={noop} />
      </Provider>,
    );

    const allProps: any = component.find(Select).first().props();

    expect(allProps.options.map((o: Option) => o.value)).toEqual([
      'compute.googleapis.com/disk/read_ops_count',
      'compute.googleapis.com/disk/throttled_read_ops_count',
    ]);
  });

  it('queries for metric descriptors matching selected metric type on component mount', () => {
    const store = createStore(() => state);
    const dispatch = spyOn(store, 'dispatch').and.callThrough();
    mount(
      <Provider store={store}>
        <Component value="compute.googleapis.com/disk/read_ops_count" onChange={noop} />
      </Provider>,
    );

    expect(dispatch).toHaveBeenCalledWith({
      type: Actions.UPDATE_STACKDRIVER_METRIC_DESCRIPTOR_FILTER,
      payload: { filter: 'compute.googleapis.com/disk/read_ops_count' },
    });
  });

  it('queries for metric descriptors on input change', () => {
    const store = createStore(() => state);
    const dispatch = spyOn(store, 'dispatch').and.callThrough();
    const component = mount(
      <Provider store={store}>
        <Component value="compute.googleapis.com/disk/read_ops_count" onChange={noop} />
      </Provider>,
    );
    const allProps: ReactSelectProps = component.find(Select).props();
    allProps.onInputChange('redis');

    expect(dispatch).toHaveBeenCalledWith({
      type: Actions.UPDATE_STACKDRIVER_METRIC_DESCRIPTOR_FILTER,
      payload: { filter: 'redis' },
    });
  });
});
