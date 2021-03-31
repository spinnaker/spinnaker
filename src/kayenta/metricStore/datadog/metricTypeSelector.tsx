import * as Creators from 'kayenta/actions/creators';
import { DISABLE_EDIT_CONFIG, DisableableReactSelect } from 'kayenta/layout/disableable';
import { ICanaryState } from 'kayenta/reducers';
import { AsyncRequestState } from 'kayenta/reducers/asyncRequest';
import * as React from 'react';
import { connect } from 'react-redux';
import { Option } from 'react-select';
import { Dispatch } from 'redux';

import { IDatadogMetricDescriptor } from './domain/IDatadogMetricDescriptor';

interface IDatadogMetricTypeSelectorDispatchProps {
  load: (filter: string) => void;
}

interface IDatadogMetricTypeSelectorStateProps {
  options: Option[];
  loading: boolean;
}

interface IDatadogMetricTypeSelectorOwnProps {
  value: string;
  onChange: (option: Option) => void;
}

export const DatadogMetricTypeSelector = ({
  loading,
  load,
  options,
  value,
  onChange,
}: IDatadogMetricTypeSelectorDispatchProps &
  IDatadogMetricTypeSelectorStateProps &
  IDatadogMetricTypeSelectorOwnProps) => {
  if (value && options.every((o) => o.value !== value)) {
    options = options.concat({ label: value, value });
  }

  return (
    <DisableableReactSelect
      isLoading={loading}
      options={options}
      onChange={onChange}
      value={value}
      placeholder={'Enter at least three characters to search.'}
      onInputChange={(input: string) => {
        load(input);
        return input;
      }}
      disabledStateKeys={[DISABLE_EDIT_CONFIG]}
    />
  );
};

export const mapStateToProps = (state: ICanaryState, ownProps: IDatadogMetricTypeSelectorOwnProps) => {
  const descriptors = state.data.metricsServiceMetadata.data as IDatadogMetricDescriptor[];
  const options: Option[] = descriptors.map((d) => ({ label: d.name, value: d.name }));
  return {
    options,
    loading: state.data.metricsServiceMetadata.load === AsyncRequestState.Requesting,
    ...ownProps,
  };
};

export const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>) => {
  return {
    load: (filter: string) => {
      dispatch(Creators.updateDatadogMetricDescriptorFilter({ filter }));
    },
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(DatadogMetricTypeSelector);
