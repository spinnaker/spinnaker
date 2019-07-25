import * as React from 'react';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import { Option } from 'react-select';
import { createSelector } from 'reselect';
import { get, chain } from 'lodash';

import { KayentaAccountType } from 'kayenta/domain';
import { ICanaryState } from 'kayenta/reducers';
import { IPrometheusMetricDescriptor } from './domain/IPrometheusMetricDescriptor';
import { AsyncRequestState } from 'kayenta/reducers/asyncRequest';
import * as Creators from 'kayenta/actions/creators';
import { DISABLE_EDIT_CONFIG, DisableableReactSelect } from 'kayenta/layout/disableable';

import './metricTypeSelector.less';

interface IPrometheusMetricTypeSelectorDispatchProps {
  load: (filter: string, metricsAccountName: string) => void;
}

interface IPrometheusMetricTypeSelectorStateProps {
  accountOptions: Array<Option<string>>;
  loading: boolean;
  metricOptions: Array<Option<string>>;
}

interface IPrometheusMetricTypeSelectorOwnProps {
  onChange: (option: Option<string>) => void;
  value: string;
}

interface IPrometheusMetricTypeSelectorState {
  selectedAccount: string;
  showAccountDropdown: boolean;
}

export type IPrometheusMetricTypeSelectorProps = IPrometheusMetricTypeSelectorDispatchProps &
  IPrometheusMetricTypeSelectorStateProps &
  IPrometheusMetricTypeSelectorOwnProps;

export class PrometheusMetricTypeSelector extends React.Component<
  IPrometheusMetricTypeSelectorProps,
  IPrometheusMetricTypeSelectorState
> {
  public constructor(props: IPrometheusMetricTypeSelectorProps) {
    super(props);
    this.state = {
      showAccountDropdown: false,
      selectedAccount: get(props, ['accountOptions', 0, 'value']),
    };
  }

  public render() {
    const { accountOptions, load, loading, metricOptions, onChange, value } = this.props;

    const metricOptionsWithSelected =
      value && metricOptions.every(o => o.value !== value)
        ? metricOptions.concat({ label: value, value })
        : metricOptions;

    return (
      <>
        <DisableableReactSelect
          isLoading={loading}
          options={metricOptionsWithSelected}
          onChange={onChange}
          value={value}
          placeholder={'Enter at least three characters to search.'}
          onInputChange={input => {
            load(input, this.state.selectedAccount);
            return input;
          }}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
        {accountOptions.length > 1 && (
          <div className="prometheus-metric-type-selector-account-hint">
            <span>Metric search is currently populating from {this.state.selectedAccount}.</span>
            <span className="btn btn-link" onClick={this.showAccountDropdown}>
              {!this.state.showAccountDropdown && 'Switch Account'}
            </span>
          </div>
        )}
        {this.state.showAccountDropdown && (
          <DisableableReactSelect
            clearable={false}
            options={accountOptions}
            onChange={this.selectAccount}
            value={this.state.selectedAccount}
            disabledStateKeys={[DISABLE_EDIT_CONFIG]}
          />
        )}
      </>
    );
  }

  private showAccountDropdown = (): void => {
    this.setState({
      showAccountDropdown: true,
    });
  };

  private selectAccount = (option: Option<string>): void => {
    this.setState({
      selectedAccount: option.value,
      showAccountDropdown: false,
    });
  };
}

const accountOptionsSelector = createSelector(
  (state: ICanaryState) => state.data.kayentaAccounts.data,
  (accounts): Array<Option<string>> => {
    return chain(accounts)
      .filter(a => a.supportedTypes.includes(KayentaAccountType.MetricsStore) && a.type === 'prometheus')
      .sortBy(a => a.name)
      .map(a => ({ label: a.name, value: a.name }))
      .value();
  },
);

const metricOptionsSelector = createSelector(
  (state: ICanaryState) => state.data.metricsServiceMetadata.data,
  (descriptors: IPrometheusMetricDescriptor[]): Array<Option<string>> =>
    descriptors.map(d => ({ label: d.name, value: d.name })),
);

const mapStateToProps = (state: ICanaryState, ownProps: IPrometheusMetricTypeSelectorOwnProps) => {
  return {
    accountOptions: accountOptionsSelector(state),
    metricOptions: metricOptionsSelector(state),
    loading: state.data.metricsServiceMetadata.load === AsyncRequestState.Requesting,
    ...ownProps,
  };
};

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>) => {
  return {
    load: (filter: string, metricsAccountName: string) => {
      dispatch(Creators.updatePrometheusMetricDescriptorFilter({ filter, metricsAccountName }));
    },
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(PrometheusMetricTypeSelector);
