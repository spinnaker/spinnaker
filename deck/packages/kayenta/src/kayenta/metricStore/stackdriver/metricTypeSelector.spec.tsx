import { fireEvent, render, screen, within } from '@testing-library/react';
import { setupUser } from '../../../../../core/src/utils/testUtils/userEvent';
import * as Actions from '../../actions';
import * as React from 'react';
import { connect, Provider } from 'react-redux';
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

  it('builds options from input descriptors and selects a metric', async () => {
    const user = setupUser();
    const onChange = vi.fn();
    render(
      <Provider store={createStore(() => state)}>
        <Component value="compute.googleapis.com/disk/read_ops_count" onChange={onChange} />
      </Provider>,
    );

    fireEvent.keyDown(screen.getByRole('combobox'), { key: 'ArrowDown', keyCode: 40 });

    const menu = within(screen.getByRole('listbox'));
    expect(menu.getByRole('option', { name: 'compute.googleapis.com/disk/read_ops_count' })).toBeVisible();
    expect(menu.getByRole('option', { name: 'compute.googleapis.com/disk/throttled_read_ops_count' })).toBeVisible();

    await user.click(menu.getByRole('option', { name: 'compute.googleapis.com/disk/throttled_read_ops_count' }));

    expect(onChange).toHaveBeenCalledWith({
      label: 'compute.googleapis.com/disk/throttled_read_ops_count',
      value: 'compute.googleapis.com/disk/throttled_read_ops_count',
    });
  });

  it('queries for metric descriptors matching selected metric type on component mount', () => {
    const store = createStore(() => state);
    const dispatch = vi.spyOn(store, 'dispatch');
    render(
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
    const dispatch = vi.spyOn(store, 'dispatch');
    render(
      <Provider store={store}>
        <Component value="compute.googleapis.com/disk/read_ops_count" onChange={noop} />
      </Provider>,
    );
    dispatch.mockClear();
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'redis' } });

    expect(dispatch).toHaveBeenCalledWith({
      type: Actions.UPDATE_STACKDRIVER_METRIC_DESCRIPTOR_FILTER,
      payload: { filter: 'redis' },
    });
  });
});
