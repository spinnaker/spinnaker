import { fireEvent, render, screen, within } from '@testing-library/react';
import { setupUser } from '../../../../../core/src/utils/testUtils/userEvent';
import * as React from 'react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';

import { noop } from '@spinnaker/core';

import { IPrometheusMetricTypeSelectorProps, PrometheusMetricTypeSelector } from './metricTypeSelector';

describe('<PrometheusMetricTypeSelector />', () => {
  const defaultProps: IPrometheusMetricTypeSelectorProps = {
    accountOptions: [
      {
        label: 'my-first-prometheus-account',
        value: 'my-first-prometheus-account',
      },
      {
        label: 'my-second-prometheus-account',
        value: 'my-second-prometheus-account',
      },
    ],
    load: noop,
    loading: false,
    metricOptions: [],
    onChange: noop,
    value: '',
  };
  const renderComponent = (props = defaultProps) =>
    render(
      <Provider store={createStore(() => ({}))}>
        <PrometheusMetricTypeSelector {...props} />
      </Provider>,
    );

  it('renders a typeahead select to search for metrics', () => {
    renderComponent();

    expect(screen.getByRole('combobox')).toBeVisible();
    expect(screen.getByText('Enter at least three characters to search.')).toBeVisible();
  });

  it('displays which account will populate the search when >1 account is configured, defaulting to the first account alphabetically', () => {
    renderComponent();

    expect(screen.getByText('Metric search is currently populating from my-first-prometheus-account.')).toBeVisible();
  });

  it('allows the user to switch which account populates the search when >1 account is configured', async () => {
    const user = setupUser();
    const load = vi.fn();
    renderComponent({ ...defaultProps, load });

    await user.click(screen.getByRole('button', { name: 'Switch Account' }));

    const accountSelect = screen.getAllByRole('combobox')[1];
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    fireEvent.keyDown(accountSelect, { key: 'ArrowDown', keyCode: 40 });
    await user.click(within(screen.getByRole('listbox')).getByRole('option', { name: 'my-second-prometheus-account' }));

    expect(screen.getByText('Metric search is currently populating from my-second-prometheus-account.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Switch Account' })).toBeVisible();
    expect(screen.getAllByRole('combobox')).toHaveLength(1);

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'requests' } });

    expect(load).toHaveBeenCalledWith('requests', 'my-second-prometheus-account');
  });

  it('does not display account selection hint when there is only one account configured', () => {
    renderComponent({
      ...defaultProps,
      accountOptions: [{ label: 'my-only-prometheus-account', value: 'my-only-prometheus-account' }],
    });

    expect(screen.queryByText(/metric search is currently populating from/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Switch Account' })).not.toBeInTheDocument();
  });
});
