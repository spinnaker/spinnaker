import * as React from 'react';
import { Option } from 'react-select';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { get } from 'lodash';
import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';
import { IUpdateListPayload, List } from 'kayenta/layout/list';
import * as Creators from 'kayenta/actions/creators';
import PrometheusMetricTypeSelector from './metricTypeSelector';
import { DISABLE_EDIT_CONFIG, DisableableReactSelect } from 'kayenta/layout/disableable';
import { IPrometheusCanaryMetricSetQueryConfig } from './domain/IPrometheusCanaryMetricSetQueryConfig';

interface IPrometheusMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IPrometheusMetricConfigurerDispatchProps {
  updateLabelBindings: (payload: IUpdateListPayload) => void;
  updateGroupBy: (payload: IUpdateListPayload) => void;
  updatePrometheusMetricQueryField<T extends keyof IPrometheusCanaryMetricSetQueryConfig>(
    field: keyof IPrometheusCanaryMetricSetQueryConfig,
    value: Option<IPrometheusCanaryMetricSetQueryConfig[T]>,
  ): void;
}

const RESOURCE_TYPES = ['gce_instance', 'aws_ec2_instance'];

const toReactSelectOptions = (values: string[]): Option<string>[] => values.map(value => ({ value, label: value }));

/*
* Component for configuring a Prometheus metric.
* */
function PrometheusMetricConfigurer({
  editingMetric,
  updateLabelBindings,
  updateGroupBy,
  updatePrometheusMetricQueryField,
}: IPrometheusMetricConfigurerStateProps & IPrometheusMetricConfigurerDispatchProps) {
  return (
    <section>
      <FormRow label="Resource Type">
        <DisableableReactSelect
          value={get(editingMetric, 'query.resourceType')}
          options={toReactSelectOptions(RESOURCE_TYPES)}
          onChange={(option: Option<string>) => updatePrometheusMetricQueryField('resourceType', option)}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
      <FormRow label="Metric Name">
        <PrometheusMetricTypeSelector
          value={get(editingMetric, 'query.metricName', '')}
          onChange={(option: Option<string>) => updatePrometheusMetricQueryField('metricName', option)}
        />
      </FormRow>
      <FormRow label="Label Bindings">
        <List list={editingMetric.query.labelBindings || []} actionCreator={updateLabelBindings} />
      </FormRow>
      <FormRow label="Group By">
        <List list={editingMetric.query.groupByFields || []} actionCreator={updateGroupBy} />
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
    updateLabelBindings: payload => dispatch(Creators.updatePrometheusLabelBindings(payload)),
    updateGroupBy: payload => dispatch(Creators.updatePrometheusGroupBy(payload)),
    updatePrometheusMetricQueryField: (field, option) =>
      dispatch(Creators.updatePrometheusMetricQueryField({ field, value: option && option.value })),
  };
}

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(PrometheusMetricConfigurer);
