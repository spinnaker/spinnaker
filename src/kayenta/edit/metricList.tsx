import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryState } from '../reducers';
import { UNGROUPED } from './groupTabs';
import MetricDetail from './metricDetail';
import { ADD_METRIC, RENAME_METRIC, REMOVE_METRIC } from '../actions/index';

interface IMetricListStateProps {
  selectedGroup: string,
  metrics: ICanaryMetricConfig[];
}

interface IMetricListDispatchProps {
  renameMetric: (event: any) => void;
  addMetric: (event: any) => void;
  removeMetric: (event: any) => void;
}

/*
 * Configures an entire list of metrics.
 */
function MetricList({ metrics, selectedGroup, renameMetric, addMetric, removeMetric }: IMetricListStateProps & IMetricListDispatchProps) {
  return (
    <section>
      <ul className="list-group">
        {metrics.map((metric, index) => (
          <li className="list-group-item" key={index}>
            <MetricDetail metric={metric} rename={renameMetric} remove={removeMetric}/>
          </li>
        ))}
      </ul>
      {(!metrics.length && selectedGroup && selectedGroup !== UNGROUPED) ? (
        <p>
          This group is empty! The group will be not be present the next time the config is loaded unless
          it is saved with at least one metric in it.
        </p>
      ) : null}
      <button data-group={selectedGroup} onClick={addMetric}>Add Metric</button>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IMetricListStateProps {
  const { selectedGroup, metricList } = state;
  let filter;
  if (!selectedGroup) {
    filter = () => true;
  } else if (selectedGroup === UNGROUPED) {
    filter = (metric: ICanaryMetricConfig) => metric.groups.length === 0;
  } else {
    filter = (metric: ICanaryMetricConfig) => metric.groups.includes(selectedGroup);
  }
  return {
    selectedGroup,
    metrics: metricList.filter(filter)
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IMetricListDispatchProps {
  return {
    renameMetric: (event: any) => {
      dispatch({
        type: RENAME_METRIC,
        id: event.target.dataset.id,
        name: event.target.value
      });
    },

    addMetric: (event: any) => {
      const group = event.target.dataset.group;
      dispatch({
        type: ADD_METRIC,
        metric: {
          // TODO: need to block saving an invalid name
          // TODO: for Atlas metrics, attempt to gather name when query changes
          name: '',
          // TODO: we should have a default service setting somewhere
          serviceName: 'atlas',
          groups: (group && group !== UNGROUPED) ? [group] : []
        }
      })
    },

    removeMetric: (event: any) => {
      dispatch({
        type: REMOVE_METRIC,
        id: event.target.dataset.id
      });
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(MetricList);
