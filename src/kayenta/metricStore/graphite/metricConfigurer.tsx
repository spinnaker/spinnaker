import * as Creators from 'kayenta/actions/creators';
import { ICanaryMetricConfig } from 'kayenta/domain';
import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import { get } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';

import GraphiteMetricTypeSelector from './metricTypeSelector';

interface IGraphiteMetricConfigurerDispatchProps {
  changeMetricName: (name: string) => void;
}

interface IGraphiteMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

type GraphiteMetricConfigurerProps = IGraphiteMetricConfigurerStateProps & IGraphiteMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', '');

/*
 * Component for configuring a Graphite metric.
 * */
function GraphiteMetricConfigurer({ changeMetricName, editingMetric }: GraphiteMetricConfigurerProps) {
  return (
    <FormRow label="Graphite Metric" inputOnly={true}>
      <GraphiteMetricTypeSelector
        value={queryFinder(editingMetric)}
        onChange={(option: string[]) => {
          changeMetricName(option[0]);
        }}
      />
    </FormRow>
  );
}

function mapStateToProps(state: ICanaryState): IGraphiteMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IGraphiteMetricConfigurerDispatchProps {
  return {
    changeMetricName: (metricName: string): void => {
      dispatch(Creators.updateGraphiteMetricName({ metricName }));
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(GraphiteMetricConfigurer);
