import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Option } from 'react-select';
import { get } from 'lodash';

import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';
import { IUpdateListPayload, List } from 'kayenta/layout/list';
import * as Creators from 'kayenta/actions/creators';
import StackdriverMetricTypeSelector from './metricTypeSelector';
import { DISABLE_EDIT_CONFIG, DisableableReactSelect } from 'kayenta/layout/disableable';
import { IStackdriverCanaryMetricSetQueryConfig } from './domain/IStackdriverCanaryMetricSetQueryConfig';

interface IStackdriverMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IStackdriverMetricConfigurerDispatchProps {
  updateStackdriverQueryField: <T extends keyof IStackdriverCanaryMetricSetQueryConfig>(
    field: keyof IStackdriverCanaryMetricSetQueryConfig,
    value: Option<IStackdriverCanaryMetricSetQueryConfig[T]>,
  ) => void;
  updateGroupBy: (payload: IUpdateListPayload) => void;
}

// TODO(dpeach): externalize these values.
const RESOURCE_TYPES = [
  'gce_instance',
  'aws_ec2_instance',
  'gae_app',
  'k8s_container',
  'k8s_pod',
  'k8s_node',
  'gke_container',
  'https_lb_rule',
  'global',
];

const CROSS_SERIES_REDUCERS = [
  'REDUCE_NONE',
  'REDUCE_MEAN',
  'REDUCE_MIN',
  'REDUCE_MAX',
  'REDUCE_SUM',
  'REDUCE_COUNT_TRUE',
  'REDUCE_COUNT_FALSE',
  'REDUCE_FRACTION_TRUE',
  'REDUCE_PERCENTILE_99',
  'REDUCE_PERCENTILE_95',
  'REDUCE_PERCENTILE_50',
  'REDUCE_PERCENTILE_05',
];

const PER_SERIES_ALIGNERS = [
  'ALIGN_NONE',
  'ALIGN_DELTA',
  'ALIGN_RATE',
  'ALIGN_INTERPOLATE',
  'ALIGN_NEXT_OLDER',
  'ALIGN_MIN',
  'ALIGN_MAX',
  'ALIGN_MEAN',
  'ALIGN_COUNT',
  'ALIGN_SUM',
  'ALIGN_STDDEV',
  'ALIGN_COUNT_TRUE',
  'ALIGN_COUNT_FALSE',
  'ALIGN_FRACTION_TRUE',
  'ALIGN_PERCENTILE_99',
  'ALIGN_PERCENTILE_95',
  'ALIGN_PERCENTILE_50',
  'ALIGN_PERCENTILE_05',
  'ALIGN_PERCENT_CHANGE',
];

const STACKDRIVER_HELP_ID_PREFIX = 'stackdriver';

const toReactSelectOptions = (values: string[]): Array<Option<string>> =>
  values.map(value => ({ value, label: value }));

/*
* Component for configuring a Stackdriver metric.
* */
function StackdriverMetricConfigurer({
  editingMetric,
  updateGroupBy,
  updateStackdriverQueryField,
}: IStackdriverMetricConfigurerStateProps & IStackdriverMetricConfigurerDispatchProps) {
  return (
    <>
      <FormRow label="Resource Type" helpId={`${STACKDRIVER_HELP_ID_PREFIX}.resourceType`}>
        <DisableableReactSelect
          value={get(editingMetric, 'query.resourceType')}
          options={toReactSelectOptions(RESOURCE_TYPES)}
          onChange={(option: Option<string>) => updateStackdriverQueryField('resourceType', option)}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
      <FormRow label="Metric Type" helpId={`${STACKDRIVER_HELP_ID_PREFIX}.metricType`}>
        <StackdriverMetricTypeSelector
          value={get(editingMetric, 'query.metricType', '')}
          onChange={(option: Option<string>) => updateStackdriverQueryField('metricType', option)}
        />
      </FormRow>
      <FormRow label="Group By" helpId={`${STACKDRIVER_HELP_ID_PREFIX}.groupBy`}>
        <List list={editingMetric.query.groupByFields || []} actionCreator={updateGroupBy} />
      </FormRow>
      <FormRow label="Aligner" helpId={`${STACKDRIVER_HELP_ID_PREFIX}.perSeriesAligner`}>
        <DisableableReactSelect
          value={get(editingMetric, 'query.perSeriesAligner')}
          options={toReactSelectOptions(PER_SERIES_ALIGNERS)}
          onChange={(option: Option<string>) => updateStackdriverQueryField('perSeriesAligner', option)}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
      <FormRow label="Reducer" helpId={`${STACKDRIVER_HELP_ID_PREFIX}.crossSeriesReducer`}>
        <DisableableReactSelect
          value={get(editingMetric, 'query.crossSeriesReducer')}
          options={toReactSelectOptions(CROSS_SERIES_REDUCERS)}
          onChange={(option: Option<string>) => updateStackdriverQueryField('crossSeriesReducer', option)}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
    </>
  );
}

function mapStateToProps(state: ICanaryState): IStackdriverMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IStackdriverMetricConfigurerDispatchProps {
  return {
    updateStackdriverQueryField: (field, option) =>
      dispatch(Creators.updateStackdriverMetricResourceField({ field, value: option && option.value })),
    updateGroupBy: payload => dispatch(Creators.updateStackdriverGroupBy(payload)),
  };
}

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(StackdriverMetricConfigurer);
