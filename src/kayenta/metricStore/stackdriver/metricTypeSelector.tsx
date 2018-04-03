import * as React from 'react';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import { Option } from 'react-select';
import { ICanaryState } from 'kayenta/reducers';
import { IStackdriverMetricDescriptor } from './domain/IStackdriverMetricDescriptor';
import { AsyncRequestState } from 'kayenta/reducers/asyncRequest';
import * as Creators from 'kayenta/actions/creators';
import { DisableableReactSelect, DISABLE_EDIT_CONFIG } from'kayenta/layout/disableable';

interface IStackdriverMetricTypeSelectorDispatchProps {
  load: (filter: string) => void;
}

interface IStackdriverMetricTypeSelectorStateProps {
  options: Option[]
  loading: boolean;
}

interface IStackdriverMetricTypeSelectorOwnProps {
  value: string;
  onChange: (option: Option) => void;
}

const StackdriverMetricTypeSelector = ({ loading, load, options, value, onChange }: IStackdriverMetricTypeSelectorDispatchProps & IStackdriverMetricTypeSelectorStateProps & IStackdriverMetricTypeSelectorOwnProps) => {
  if (value && options.every(o => o.value !== value)) {
    options = options.concat({ label: value, value });
  }

  return (
    <DisableableReactSelect
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
      disabledStateKeys={[DISABLE_EDIT_CONFIG]}
    />
  );
};

const mapStateToProps = (state: ICanaryState, ownProps: IStackdriverMetricTypeSelectorOwnProps) => {
  const descriptors = state.data.metricsServiceMetadata.data as IStackdriverMetricDescriptor[];
  const options: Option[] = descriptors.map(d => ({ label: d.type, value: d.type }));
  return {
    options,
    loading: state.data.metricsServiceMetadata.load === AsyncRequestState.Requesting,
    ...ownProps,
  };
};

const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>) => {
  return {
    load: (filter: string) => {
      dispatch(Creators.updateStackdriverMetricDescriptorFilter({ filter }));
    },
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(StackdriverMetricTypeSelector);
