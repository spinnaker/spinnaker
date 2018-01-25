import * as React from 'react';
import Select, { Option } from 'react-select';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { get } from 'lodash';
import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';
import { getMetricTypes } from './metricType.service';
import { IUpdateListPayload, List } from 'kayenta/layout/list';
import * as Actions from 'kayenta/actions';
import * as Creators from 'kayenta/actions/creators';

interface IStackdriverMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IStackdriverMetricConfigurerDispatchProps {
  updateMetricType: (option: Option) => void;
  updateGroupBy: (payload: IUpdateListPayload) => void;
}

/*
* Component for configuring a Stackdriver metric.
* */
function StackdriverMetricConfigurer({ editingMetric, updateMetricType, updateGroupBy }: IStackdriverMetricConfigurerStateProps & IStackdriverMetricConfigurerDispatchProps) {
  // TODO(dpeach): finish this.
  // Will probably have to load these asynchronously somewhere else.
  const metricTypeOptions: Option[] = getMetricTypes().map(type =>
    ({
      label: type.split('/').slice(1).join('/'), // Omit API prefix.
      value: type
    })
  );

  return (
    <section>
      <FormRow label="Metric Type">
        <Select
          value={get(editingMetric, 'query.metricType', '')}
          options={metricTypeOptions}
          clearable={false}
          onChange={updateMetricType}
        />
      </FormRow>
      <FormRow label="Group By">
        <List
          list={editingMetric.query.groupByFields || []}
          actionCreator={updateGroupBy}
        />
      </FormRow>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IStackdriverMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IStackdriverMetricConfigurerDispatchProps {
  return {
    updateMetricType: (option: Option): void => {
      dispatch({
        type: Actions.UPDATE_STACKDRIVER_METRIC_TYPE,
        metricType: option.value,
      });
    },
    updateGroupBy: payload => dispatch(Creators.updateStackdriverGroupBy(payload)),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(StackdriverMetricConfigurer);
