import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryState } from '../reducers';
import { UNGROUPED } from './groupTabs';
import MetricDetail from './metricDetail';
import * as Creators from '../actions/creators';
import { CanarySettings } from 'kayenta/canary.settings';

interface IMetricListStateProps {
  selectedGroup: string;
  metrics: ICanaryMetricConfig[];
}

interface IMetricListDispatchProps {
  addMetric: (event: any) => void;
  editMetric: (event: any) => void;
  removeMetric: (event: any) => void;
}

/*
 * Configures an entire list of metrics.
 */
function MetricList({ metrics, selectedGroup, addMetric, editMetric, removeMetric }: IMetricListStateProps & IMetricListDispatchProps) {
  return (
    <section>
      <ul className="list-group">
        {metrics.map((metric, index) => (
          <li className="list-group-item" key={index}>
            <MetricDetail metric={metric} edit={editMetric} remove={removeMetric}/>
          </li>
        ))}
      </ul>
      {(!metrics.length && selectedGroup && selectedGroup !== UNGROUPED) ? (
        <p>
          This group is empty! The group will be not be present the next time the config is loaded unless
          it is saved with at least one metric in it.
        </p>
      ) : null}
      <button className="passive" data-group={selectedGroup} onClick={addMetric}>Add Metric</button>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IMetricListStateProps {
  const selectedGroup = state.selectedConfig.group.selected;
  const metricList = state.selectedConfig.metricList;

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
    metrics: metricList.filter(filter),
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IMetricListDispatchProps {
  return {
    addMetric: (event: any) => {
      const group = event.target.dataset.group;
      dispatch(Creators.addMetric({
        metric: {
          // TODO: need to block saving an invalid name
          // TODO: for Atlas metrics, attempt to gather name when query changes
          name: '',
          serviceName: CanarySettings.metricStore,
          groups: (group && group !== UNGROUPED) ? [group] : []
        }
      }));
    },

    editMetric: (event: any) => {
      dispatch(Creators.editMetricBegin({ id: event.target.dataset.id }));
    },

    removeMetric: (event: any) => {
      dispatch(Creators.removeMetric({ id: event.target.dataset.id }));
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(MetricList);
