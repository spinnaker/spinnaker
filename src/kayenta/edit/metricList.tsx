import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryState } from '../reducers';
import MetricDetail from './metricDetail';
import { RENAME_METRIC } from '../actions/index';

interface IMetricListStateProps {
  metrics: ICanaryMetricConfig[];
}

interface IMetricListDispatchProps {
  changeName: any;
}

/*
 * Configures an entire list of metrics.
 */
function MetricList({ metrics, changeName }: IMetricListStateProps & IMetricListDispatchProps) {
  return (
    <section>
      <h2>Metrics</h2>
      <ul className="list-group">
        {metrics.map((metric, index) => (
          // TODO: put id on metric? name can change by edit, index can change by remove operation
          // unless remove leaves a null entry instead of deleting the index.
          <li className="list-group-item" key={index}>
            <MetricDetail id={index} metric={metric} changeName={changeName}/>
          </li>
        ))}
      </ul>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IMetricListStateProps {
  return {
    metrics: state.metricList,
  };
}

function mapDispatchToProps(dispatch: any): IMetricListDispatchProps {
  return {
    changeName: (event: any) => {
      dispatch({
        type: RENAME_METRIC,
        id: event.target.dataset.id,
        name: event.target.value
      });
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(MetricList);
