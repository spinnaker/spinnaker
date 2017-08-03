import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import FormRow from '../../layout/formRow';
import { ICanaryState } from '../../reducers/index';
import { ICanaryMetricConfig } from '../../domain/ICanaryConfig';
import { UPDATE_STACKDRIVER_METRIC_TYPE } from '../../actions/index';

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
  return (
    <FormRow label="Metric Type">
      <input
        type="text"
        className="form-control"
        value={editingMetric.query.metricType}
        onChange={updateMetricType}
      />
    </FormRow>
  );
}

function mapStateToProps(state: ICanaryState): IStackdriverMetricConfigurerStateProps {
  return {
    editingMetric: state.editingMetric,
  }
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IStackdriverMetricConfigurerDispatchProps {
  return {
    updateMetricType: (event: any): void => {
      dispatch({
        type: UPDATE_STACKDRIVER_METRIC_TYPE,
        metricType: event.target.value,
      });
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(StackdriverMetricConfigurer);
