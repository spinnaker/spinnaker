import * as React from 'react';
import * as Select from 'react-select';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { get } from 'lodash';
import FormRow from '../../layout/formRow';
import { ICanaryState } from '../../reducers/index';
import { ICanaryMetricConfig } from '../../domain/ICanaryConfig';
import { UPDATE_STACKDRIVER_METRIC_TYPE } from '../../actions/index';
import { getMetricTypes } from './metricType.service';

interface IStackdriverMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IStackdriverMetricConfigurerDispatchProps {
  updateMetricType: (event: any) => void;
}

/*
* Component for configuring a Stackdriver metric.
* */
function StackdriverMetricConfigurer({ editingMetric, updateMetricType }: IStackdriverMetricConfigurerStateProps & IStackdriverMetricConfigurerDispatchProps) {
  // TODO(dpeach): finish this.
  // Will probably have to load these asynchronously somewhere else.
  const metricTypeOptions: Select.Option[] = getMetricTypes().map(type =>
    ({
      label: type.split('/').slice(1).join('/'), // Omit API prefix.
      value: type
    })
  );

  return (
    <FormRow label="Metric Type">
      <Select
        value={get(editingMetric, 'query.metricType', '')}
        options={metricTypeOptions}
        clearable={false}
        onChange={updateMetricType}
      />
    </FormRow>
  );
}

function mapStateToProps(state: ICanaryState): IStackdriverMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  }
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IStackdriverMetricConfigurerDispatchProps {
  return {
    updateMetricType: (option: Select.Option): void => {
      dispatch({
        type: UPDATE_STACKDRIVER_METRIC_TYPE,
        metricType: option.value,
      });
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(StackdriverMetricConfigurer);
