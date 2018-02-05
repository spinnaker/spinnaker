import * as React from 'react';
import * as Select from 'react-select';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { get } from 'lodash';
import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';
import { IUpdateListPayload, List } from 'kayenta/layout/list';
import * as Creators from 'kayenta/actions/creators';
import StackdriverMetricTypeSelector from './metricTypeSelector';

interface IStackdriverMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IStackdriverMetricConfigurerDispatchProps {
  updateMetricType: (option: Select.Option) => void;
  updateGroupBy: (payload: IUpdateListPayload) => void;
}

/*
* Component for configuring a Stackdriver metric.
* */
function StackdriverMetricConfigurer({ editingMetric, updateMetricType, updateGroupBy }: IStackdriverMetricConfigurerStateProps & IStackdriverMetricConfigurerDispatchProps) {
  return (
    <section>
      <FormRow label="Metric Type">
        <StackdriverMetricTypeSelector
          value={get(editingMetric, 'query.metricType', '')}
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
    updateMetricType: (option: Select.Option): void => {
      dispatch(Creators.updateStackdriverMetricType({
        metricType: (option ? option.value : null) as string,
      }));
    },
    updateGroupBy: payload => dispatch(Creators.updateStackdriverGroupBy(payload)),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(StackdriverMetricConfigurer);
