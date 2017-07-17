import * as React from 'react';
import { connect } from 'react-redux';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryState } from '../reducers';
import MetricDetail from './metricDetail';
import OpenDeleteModalButton from './openDeleteModalButton';
import { ADD_METRIC, RENAME_METRIC } from '../actions/index';

interface IMetricListStateProps {
  metrics: ICanaryMetricConfig[];
}

interface IMetricListDispatchProps {
  changeName: any;
  addMetric: any;
}

/*
 * Configures an entire list of metrics.
 */
function MetricList({ metrics, changeName, addMetric }: IMetricListStateProps & IMetricListDispatchProps) {
  return (
    <section>
      <h2>Metrics</h2>
      {/*TODO: this button should not go here, but there is no good spot for it now.*/}
      <OpenDeleteModalButton/>
      <ul className="list-group">
        {metrics.map((metric, index) => (
          // TODO: put id on metric? name can change by edit, index can change by remove operation
          // unless remove leaves a null entry instead of deleting the index.
          <li className="list-group-item" key={index}>
            <MetricDetail id={index} metric={metric} changeName={changeName}/>
          </li>
        ))}
      </ul>
      <button onClick={addMetric}>Add Metric</button>
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
    },

    addMetric: () => {
      dispatch({
        type: ADD_METRIC,
        metric: {
          // TODO: need to block saving an invalid name
          // TODO: for Atlas metrics, attempt to gather name when query changes
          name: '',
          // TODO: we should have a default service setting somewhere
          serviceName: 'atlas'
        }
      })
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(MetricList);
