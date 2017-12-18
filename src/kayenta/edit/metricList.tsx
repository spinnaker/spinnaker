import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { ICanaryState } from '../reducers';
import { UNGROUPED } from './groupTabs';
import * as Creators from '../actions/creators';
import { CanarySettings } from 'kayenta/canary.settings';
import { ITableColumn, Table } from '../layout/table';
import ChangeMetricGroup from './changeMetricGroup';

interface IMetricListStateProps {
  selectedGroup: string;
  metrics: ICanaryMetricConfig[];
  showGroups: boolean;
}

interface IMetricListDispatchProps {
  addMetric: (event: any) => void;
  editMetric: (event: any) => void;
  removeMetric: (event: any) => void;
}

/*
 * Configures an entire list of metrics.
 */
function MetricList({ metrics, selectedGroup, showGroups, addMetric, editMetric, removeMetric }: IMetricListStateProps & IMetricListDispatchProps) {
  const columns: ITableColumn<ICanaryMetricConfig>[] = [
    {
      label: 'Metric Name',
      width: 6,
      getContent: metric => <span>{metric.name || '(new)'}</span>,
    },
    {
      label: 'Groups',
      width: 3,
      getContent: metric => <span>{metric.groups.join(', ')}</span>,
      hide: !showGroups,
    },
    {
      width: 1,
      getContent: metric => (
        <div className="horizontal center">
          <i
            className="fa fa-edit"
            data-id={metric.id}
            onClick={editMetric}
          />
          <ChangeMetricGroup metric={metric}/>
          <i
            className="fa fa-trash"
            data-id={metric.id}
            onClick={removeMetric}
          />
        </div>
      ),
    }
  ];

  return (
    <section>
      <Table
        columns={columns}
        rows={metrics}
        rowKey={metric => metric.id}
      />
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
    showGroups: !selectedGroup || metricList.filter(filter).some(metric => metric.groups.length > 1),
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
          id: '[new]',
          analysisConfigurations: {},
          name: '',
          query: {
            type: CanarySettings.metricStore
          },
          groups: (group && group !== UNGROUPED) ? [group] : [],
          isNew: true,
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
