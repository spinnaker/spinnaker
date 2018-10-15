import * as React from 'react';
import { connect } from 'react-redux';
import { Dispatch } from 'redux';
import { Option } from 'react-select';
import { ICanaryState } from 'kayenta/reducers';
import { IStackdriverMetricDescriptor } from './domain/IStackdriverMetricDescriptor';
import { AsyncRequestState } from 'kayenta/reducers/asyncRequest';
import * as Creators from 'kayenta/actions/creators';
import { DisableableReactSelect, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';

export interface IStackdriverMetricTypeSelectorDispatchProps {
  load: (filter: string) => void;
}

export interface IStackdriverMetricTypeSelectorStateProps {
  descriptors: IStackdriverMetricDescriptor[];
  loading: boolean;
}

export interface IStackdriverMetricTypeSelectorOwnProps {
  value: string;
  onChange: (option: Option) => void;
}

type IStackdriverMetricTypeProps = IStackdriverMetricTypeSelectorDispatchProps &
  IStackdriverMetricTypeSelectorStateProps &
  IStackdriverMetricTypeSelectorOwnProps;

const MetricField = ({
  descriptor,
  field,
  label,
}: {
  descriptor: IStackdriverMetricDescriptor;
  field: keyof IStackdriverMetricDescriptor;
  label: string;
}) => {
  if (!descriptor || !descriptor[field]) {
    return null;
  }
  return (
    <div>
      <strong>{label}: </strong>
      {descriptor[field]}
    </div>
  );
};

export class StackdriverMetricTypeSelector extends React.Component<IStackdriverMetricTypeProps> {
  public componentDidMount() {
    this.props.load(this.props.value);
  }

  public render() {
    const { loading, load, descriptors, value, onChange } = this.props;

    let options: Option[] = descriptors.map(d => ({ label: d.type, value: d.type }));
    if (value && options.every(o => o.value !== value)) {
      options = options.concat({ label: value, value });
    }

    return (
      <DisableableReactSelect
        isLoading={loading}
        options={options}
        onChange={onChange}
        value={value}
        optionRenderer={(option: Option<string>) => {
          const descriptor = descriptors.find(d => d.type === option.value);
          if (!descriptor) {
            return <span>{option.label}</span>;
          }

          return (
            <>
              <MetricField descriptor={descriptor} field="type" label="Metric" />
              <MetricField descriptor={descriptor} field="description" label="Description" />
              <MetricField descriptor={descriptor} field="unit" label="Unit" />
              <MetricField descriptor={descriptor} field="metricKind" label="Kind" />
              <MetricField descriptor={descriptor} field="valueType" label="Value type" />
            </>
          );
        }}
        placeholder={'Enter at least three characters to search.'}
        onInputChange={input => {
          load(input);
          return input;
        }}
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
      />
    );
  }
}

export const mapStateToProps = (state: ICanaryState, ownProps: IStackdriverMetricTypeSelectorOwnProps) => {
  const descriptors = state.data.metricsServiceMetadata.data as IStackdriverMetricDescriptor[];
  return {
    descriptors,
    loading: state.data.metricsServiceMetadata.load === AsyncRequestState.Requesting,
    ...ownProps,
  };
};

export const mapDispatchToProps = (dispatch: Dispatch<ICanaryState>) => {
  return {
    load: (filter: string) => {
      dispatch(Creators.updateStackdriverMetricDescriptorFilter({ filter }));
    },
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(StackdriverMetricTypeSelector);
