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
import PrometheusMetricTypeSelector from './metricTypeSelector';

interface IPrometheusMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IPrometheusMetricConfigurerDispatchProps {
  updateMetricType: (option: Select.Option) => void;
  updateLabelBindings: (payload: IUpdateListPayload) => void;
  updateGroupBy: (payload: IUpdateListPayload) => void;
}

/*
* Component for configuring a Prometheus metric.
* */
function PrometheusMetricConfigurer({ editingMetric, updateMetricType, updateLabelBindings, updateGroupBy }: IPrometheusMetricConfigurerStateProps & IPrometheusMetricConfigurerDispatchProps) {
  return (
    <section>
      <FormRow label="Metric Name">
        <PrometheusMetricTypeSelector
          value={get(editingMetric, 'query.metricName', '')}
          onChange={updateMetricType}
        />
      </FormRow>
      <FormRow label="Label Bindings">
        <List
          list={editingMetric.query.labelBindings || []}
          actionCreator={updateLabelBindings}
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

function mapStateToProps(state: ICanaryState): IPrometheusMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IPrometheusMetricConfigurerDispatchProps {
  return {
    updateMetricType: (option: Select.Option): void => {
      dispatch(Creators.updatePrometheusMetricType({
        metricName: (option ? option.value : null) as string,
      }));
    },
    updateLabelBindings: payload => dispatch(Creators.updatePrometheusLabelBindings(payload)),
    updateGroupBy: payload => dispatch(Creators.updatePrometheusGroupBy(payload)),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(PrometheusMetricConfigurer);
