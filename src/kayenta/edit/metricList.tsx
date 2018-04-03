import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import * as classNames from 'classnames';
import { noop } from '@spinnaker/core';
import { ICanaryMetricConfig } from 'kayenta/domain';
import { ICanaryState } from 'kayenta/reducers';
import * as Creators from 'kayenta/actions/creators';
import { ITableColumn, Table } from 'kayenta/layout/table';
import ChangeMetricGroupModal from './changeMetricGroupModal';
import { DISABLE_EDIT_CONFIG, DisableableButton } from 'kayenta/layout/disableable';

interface IMetricListStateProps {
  selectedGroup: string;
  metrics: ICanaryMetricConfig[];
  showGroups: boolean;
  changingGroupMetric: ICanaryMetricConfig;
  groupList: string[];
  metricStore: string;
  disableEdit: boolean;
}

interface IMetricListDispatchProps {
  addMetric: (event: any) => void;
  editMetric: (event: any) => void;
  removeMetric: (event: any) => void;
  openChangeMetricGroupModal: (event: any) => void;
}

/*
 * Configures an entire list of metrics.
 */
function MetricList({ metrics, groupList, selectedGroup, showGroups, addMetric, editMetric, removeMetric, changingGroupMetric, openChangeMetricGroupModal, metricStore, disableEdit }: IMetricListStateProps & IMetricListDispatchProps) {

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
          <i
            className="far fa-folder"
            data-id={metric.id}
            onClick={openChangeMetricGroupModal}
          />
          <i
            className={classNames('fa', 'fa-trash', {
              disabled: disableEdit,
            })}
            data-id={metric.id}
            onClick={disableEdit ? noop : removeMetric}
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
        headerClassName="background-white"
      />
      {(!metrics.length && selectedGroup) ? (
        <p>
          This group is empty! The group will be not be present the next time the config is loaded unless
          it is saved with at least one metric in it.
        </p>
      ) : null}
      {changingGroupMetric && <ChangeMetricGroupModal metric={changingGroupMetric}/>}
      <DisableableButton
        className="passive"
        data-group={selectedGroup}
        data-default={groupList[0]}
        data-metric-store={metricStore}
        onClick={addMetric}
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
      >
        Add Metric
      </DisableableButton>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IMetricListStateProps {
  const selectedGroup = state.selectedConfig.group.selected;
  const metricList = state.selectedConfig.metricList;

  let filter;
  if (!selectedGroup) {
    filter = () => true;
  } else {
    filter = (metric: ICanaryMetricConfig) => metric.groups.includes(selectedGroup);
  }
  return {
    selectedGroup,
    groupList: state.selectedConfig.group.list,
    metrics: metricList.filter(filter),
    showGroups: !selectedGroup || metricList.filter(filter).some(metric => metric.groups.length > 1),
    changingGroupMetric: state.selectedConfig.metricList.find(m =>
      m.id === state.selectedConfig.changeMetricGroup.metric),
    metricStore: state.selectedConfig.selectedStore,
    disableEdit: state.app.disableConfigEdit,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IMetricListDispatchProps {
  return {
    addMetric: (event: any) => {
      const group = event.target.dataset.group || event.target.dataset.default;
      dispatch(Creators.addMetric({
        metric: {
          // TODO: need to block saving an invalid name
          // TODO: for Atlas metrics, attempt to gather name when query changes
          id: '[new]',
          analysisConfigurations: {},
          name: '',
          query: {
            type: event.target.dataset.metricStore,
            serviceType: event.target.dataset.metricStore,
          },
          groups: group ? [group] : [],
          scopeName: 'default',
          isNew: true,
        }
      }));
    },
    editMetric: (event: any) => {
      dispatch(Creators.editMetricBegin({ id: event.target.dataset.id }));
    },
    removeMetric: (event: any) => {
      dispatch(Creators.removeMetric({ id: event.target.dataset.id }));
    },
    openChangeMetricGroupModal: (event: any) =>
      dispatch(Creators.changeMetricGroup({ id: event.target.dataset.id })),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(MetricList);
