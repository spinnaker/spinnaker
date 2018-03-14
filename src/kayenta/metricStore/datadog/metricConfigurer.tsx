import * as React from 'react';
import { get } from 'lodash';
import { Action } from 'redux';
import { connect } from 'react-redux';
import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import * as Creators from 'kayenta/actions/creators';
import KayentaInput from 'kayenta/layout/kayentaInput';
import { ICanaryMetricConfig } from 'kayenta/domain';
import autoBindMethods from 'class-autobind-decorator';

interface IDatadogMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IDatadogMetricConfigurerDispatchProps {
  changeMetricName: (name: string) => void;
}

type DatadogMetricConfigurerProps = IDatadogMetricConfigurerStateProps & IDatadogMetricConfigurerDispatchProps;

export const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', '');

/*
* Component for configuring a Datadog metric.
* */
@autoBindMethods
class DatadogMetricConfigurer extends React.Component<DatadogMetricConfigurerProps> {
  public onChange(e: React.ChangeEvent<HTMLInputElement>) {
    this.props.changeMetricName(e.target.value);
  }

  public render() {
    const { editingMetric } = this.props;
    return (
      <section>
        <FormRow label="Datadog Metric">
          <KayentaInput
            type="text"
            value={queryFinder(editingMetric)}
            onChange={this.onChange}
          />
        </FormRow>
      </section>
    );
  }
}

function mapStateToProps(state: ICanaryState): IDatadogMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IDatadogMetricConfigurerDispatchProps {
  return {
    changeMetricName: (metricName: string): void => {
      dispatch(Creators.updateDatadogMetricName({ metricName }));
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(DatadogMetricConfigurer);
