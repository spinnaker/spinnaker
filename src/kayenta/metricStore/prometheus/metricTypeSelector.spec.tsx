import * as React from 'react';
import { shallow } from 'enzyme';

import { noop } from '@spinnaker/core';

import { DisableableReactSelect } from 'kayenta/layout/disableable';
import { IPrometheusMetricTypeSelectorProps, PrometheusMetricTypeSelector } from './metricTypeSelector';

describe('<PrometheusMetricTypeSelector />', () => {
  let wrapper: any;
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

  it('renders a typeahead select to search for metrics', () => {
    wrapper = shallow(<PrometheusMetricTypeSelector {...defaultProps} />);
    expect(wrapper.find(DisableableReactSelect).length).toEqual(1);
    expect(wrapper.find(DisableableReactSelect).props().placeholder).toEqual(
      'Enter at least three characters to search.',
    );
  });

  it('displays which account will populate the search when >1 account is configured, defaulting to the first account alphabetically', () => {
    wrapper = shallow(<PrometheusMetricTypeSelector {...defaultProps} />);
    expect(
      wrapper
        .find('.prometheus-metric-type-selector-account-hint span')
        .at(0)
        .text(),
    ).toEqual('Metric search is currently populating from my-first-prometheus-account.');
  });

  it('allows the user to switch which account populates the search when >1 account is configured', () => {
    wrapper = shallow(<PrometheusMetricTypeSelector {...defaultProps} />);
    expect(wrapper.find('.btn').text()).toEqual('Switch Account');
    wrapper.find('.btn').simulate('click');
    expect(wrapper.state('showAccountDropdown')).toEqual(true);
    expect(wrapper.find(DisableableReactSelect).length).toEqual(2);
  });

  it('does not display account selection hint when there is only one account configured', () => {
    wrapper = shallow(
      <PrometheusMetricTypeSelector
        {...defaultProps}
        accountOptions={[{ label: 'my-only-prometheus-account', value: 'my-only-prometheus-account' }]}
      />,
    );
    expect(wrapper.find('.prometheus-metric-type-selector-account-hint').length).toEqual(0);
    expect(wrapper.find('.btn').length).toEqual(0);
  });
});
