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
import RadioChoice from 'kayenta/layout/radioChoice';
import {
  IPrometheusCanaryMetricSetQueryConfig,
  PrometheusQueryType,
} from './domain/IPrometheusCanaryMetricSetQueryConfig';
import { queryTypeSelector } from 'kayenta/selectors/filterTemplatesSelectors';

interface IPrometheusMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
  queryType: PrometheusQueryType;
}

interface IPrometheusMetricConfigurerDispatchProps {
  updateLabelBindings: (payload: IUpdateListPayload) => void;
  updateGroupBy: (payload: IUpdateListPayload) => void;
  updateFilterQueryType: (queryType: PrometheusQueryType) => void;
  updatePrometheusMetricQueryField: <T extends keyof IPrometheusCanaryMetricSetQueryConfig>(
    field: keyof IPrometheusCanaryMetricSetQueryConfig,
    value: Option<IPrometheusCanaryMetricSetQueryConfig[T]>,
  ) => void;
}

const RESOURCE_TYPES = ['gce_instance', 'aws_ec2_instance'];

const toReactSelectOptions = (values: string[]): Array<Option<string>> =>
  values.map(value => ({ value, label: value }));

/*
* Component for configuring a Prometheus metric.
* */
function PrometheusMetricConfigurer({
  editingMetric,
  queryType,
  updateLabelBindings,
  updateGroupBy,
  updateFilterQueryType,
  updatePrometheusMetricQueryField,
}: IPrometheusMetricConfigurerStateProps & IPrometheusMetricConfigurerDispatchProps) {
  return (
    <>
      <FormRow label="Query Type" helpId="canary.config.prometheus.queryType">
        <RadioChoice
          value={PrometheusQueryType.DEFAULT}
          label="Default"
          name="queryType"
          current={queryType}
          action={() => updateFilterQueryType(PrometheusQueryType.DEFAULT)}
        />
        <RadioChoice
          value={PrometheusQueryType.PROMQL}
          label="PromQL"
          name="queryType"
          current={queryType}
          action={() => updateFilterQueryType(PrometheusQueryType.PROMQL)}
        />
      </FormRow>
      {queryType === PrometheusQueryType.DEFAULT && (
        <>
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
        </>
      )}
    </>
  );
}

function mapStateToProps(state: ICanaryState): IPrometheusMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
    queryType: queryTypeSelector(state),
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IPrometheusMetricConfigurerDispatchProps {
  return {
    updateLabelBindings: payload => dispatch(Creators.updatePrometheusLabelBindings(payload)),
    updateGroupBy: payload => dispatch(Creators.updatePrometheusGroupBy(payload)),
    updatePrometheusMetricQueryField: (field, option) =>
      dispatch(Creators.updatePrometheusMetricQueryField({ field, value: option && option.value })),
    updateFilterQueryType: (value: PrometheusQueryType) => {
      dispatch(Creators.updatePrometheusMetricQueryField({ field: 'queryType', value }));
      dispatch(Creators.editTemplateCancel()); // clear template editing
      dispatch(Creators.selectTemplate({ name: null })); // deselect template
      dispatch(Creators.editInlineTemplate({ value: '' })); // clear inline template
    },
  };
}

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(PrometheusMetricConfigurer);
