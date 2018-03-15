import * as React from 'react';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import Select, { Option } from 'react-select';
import { ICanaryState } from 'kayenta/reducers';
import { IPrometheusMetricDescriptor } from './domain/IPrometheusMetricDescriptor';
import { AsyncRequestState } from 'kayenta/reducers/asyncRequest';
import * as Creators from 'kayenta/actions/creators';

interface IPrometheusMetricTypeSelectorDispatchProps {
  load: (filter: string) => void;
}

interface IPrometheusMetricTypeSelectorStateProps {
  options: Option[]
  loading: boolean;
}

interface IPrometheusMetricTypeSelectorOwnProps {
  value: string;
  onChange: (option: Option) => void;
}

const PrometheusMetricTypeSelector = ({ loading, load, options, value, onChange }: IPrometheusMetricTypeSelectorDispatchProps & IPrometheusMetricTypeSelectorStateProps & IPrometheusMetricTypeSelectorOwnProps) => {
  if (value && options.every(o => o.value !== value)) {
    options = options.concat({ label: value, value });
  }

  return (
    <Select
      isLoading={loading}
      options={options}
      onChange={onChange}
      value={value}
      placeholder={'Enter at least three characters to search.'}
      onInputChange={
        input => {
          load(input);
          return input;
        }
      }
    />
  );
};

const mapStateToProps = (state: ICanaryState, ownProps: IPrometheusMetricTypeSelectorOwnProps) => {
  const descriptors = state.data.metricsServiceMetadata.data as IPrometheusMetricDescriptor[];
  const options: Option[] = descriptors.map(d => ({ label: d.name, value: d.name }));
  return {
    options,
    loading: state.data.metricsServiceMetadata.load === AsyncRequestState.Requesting,
    ...ownProps,
  };
};

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>) => {
  return {
    load: (filter: string) => {
      dispatch(Creators.updatePrometheusMetricDescriptorFilter({ filter }));
    },
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(PrometheusMetricTypeSelector);
