import classNames from 'classnames';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryMetricConfig } from 'kayenta/domain';
import { DISABLE_EDIT_CONFIG, DisableableButton } from 'kayenta/layout/disableable';
import { ITableColumn, NativeTable } from 'kayenta/layout/table';
import { ICanaryState } from 'kayenta/reducers';
import { cloneDeep } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';

import { Tooltip } from '@spinnaker/core';

import ChangeMetricGroupModal from './changeMetricGroupModal';

import './metricList.less';

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
  copyMetric: (metric: ICanaryMetricConfig) => void;
  removeMetric: (event: any) => void;
  openChangeMetricGroupModal: (event: any) => void;
}

function FailOn({ metric }: { metric: ICanaryMetricConfig }) {
  const direction = metric.analysisConfigurations?.canary?.direction;
  const isCritical = metric.analysisConfigurations?.canary?.critical;
  const tooltipSuffix = isCritical ? '(critical — if this metric fails, the entire canary will fail)' : '';
  const classes = classNames('metric-fail-on-icon', 'fas', 'sp-margin-xs-right', { critical: isCritical });
  if (direction === 'decrease') {
    return (
      <Tooltip value={`decrease ${tooltipSuffix}`}>
        <i className={`fa-caret-square-down ${classes}`} />
      </Tooltip>
    );
  }
  if (direction === 'increase') {
    return (
      <Tooltip value={`increase ${tooltipSuffix}`}>
        <i className={`fa-caret-square-up ${classes}`} />
      </Tooltip>
    );
  }
  return (
    <Tooltip value={`increase OR decrease ${tooltipSuffix}`}>
      <span>
        <i className={`fa-caret-square-down ${classes}`} />
        <i className={`fa-caret-square-up ${classes}`} />
      </span>
    </Tooltip>
  );
}

/*
 * Configures an entire list of metrics.
 */
function MetricList({
  metrics,
  groupList,
  selectedGroup,
  showGroups,
  addMetric,
  editMetric,
  copyMetric,
  removeMetric,
  changingGroupMetric,
  openChangeMetricGroupModal,
  metricStore,
  disableEdit,
}: IMetricListStateProps & IMetricListDispatchProps) {
  const columns: Array<ITableColumn<ICanaryMetricConfig>> = [
    {
      label: 'Metric Name',
      getContent: (metric) => <span>{metric.name || '(new)'}</span>,
    },
    {
      label: 'Fail On',
      getContent: (metric) => <FailOn metric={metric} />,
    },
    {
      label: 'Groups',
      getContent: (metric) => <span>{metric.groups.join(', ')}</span>,
      hide: () => !showGroups,
    },
    {
      getContent: (metric) => (
        <div className="horizontal pull-right metrics-action-buttons">
          <button className="link" data-id={metric.id} onClick={editMetric}>
            {disableEdit ? 'View' : 'Edit'}
          </button>
          <button className="link" data-id={metric.id} disabled={disableEdit} onClick={openChangeMetricGroupModal}>
            Move Group
          </button>
          <button className="link" data-id={metric.id} disabled={disableEdit} onClick={() => copyMetric(metric)}>
            Copy
          </button>
          <button className="link" data-id={metric.id} disabled={disableEdit} onClick={removeMetric}>
            Delete
          </button>
        </div>
      ),
    },
  ];

  return (
    <>
      <NativeTable columns={columns} rows={metrics} rowKey={(metric) => metric.id} className="header-white" />
      {!metrics.length && selectedGroup ? (
        <p>
          This group is empty! The group will be not be present the next time the config is loaded unless it is saved
          with at least one metric in it.
        </p>
      ) : null}
      {changingGroupMetric && <ChangeMetricGroupModal metric={changingGroupMetric} />}
      <DisableableButton
        className="passive self-left"
        data-group={selectedGroup}
        data-default={groupList[0]}
        data-metric-store={metricStore}
        onClick={addMetric}
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
      >
        Add Metric
      </DisableableButton>
    </>
  );
}

function mapStateToProps(state: ICanaryState): IMetricListStateProps {
  const selectedGroup = state.selectedConfig.group.selected;
  const metricList = state.selectedConfig.metricList;

  const filter = selectedGroup ? (metric: ICanaryMetricConfig) => metric.groups.includes(selectedGroup) : () => true;
  return {
    selectedGroup,
    groupList: state.selectedConfig.group.list,
    metrics: metricList.filter(filter),
    showGroups: !selectedGroup || metricList.filter(filter).some((metric) => metric.groups.length > 1),
    changingGroupMetric: state.selectedConfig.metricList.find(
      (m) => m.id === state.selectedConfig.changeMetricGroup.metric,
    ),
    metricStore: state.selectedConfig.selectedStore,
    disableEdit: state.app.disableConfigEdit,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IMetricListDispatchProps {
  return {
    addMetric: (event: any) => {
      const group = event.target.dataset.group || event.target.dataset.default;
      dispatch(
        Creators.addMetric({
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
            scopeName: 'default', // scopeName always defaults to `default` and is not configurable from the UI
            isNew: true,
          },
        }),
      );
    },
    copyMetric: (metric: ICanaryMetricConfig) => {
      const metricCopy: ICanaryMetricConfig = cloneDeep(metric);
      metricCopy.id = '[new]';
      metricCopy.isNew = true;
      metricCopy.name = '';
      dispatch(Creators.addMetric({ metric: metricCopy }));
    },
    editMetric: (event: any) => {
      dispatch(Creators.editMetricBegin({ id: event.target.dataset.id }));
    },
    removeMetric: (event: any) => {
      dispatch(Creators.removeMetric({ id: event.target.dataset.id }));
    },
    openChangeMetricGroupModal: (event: any) => dispatch(Creators.changeMetricGroup({ id: event.target.dataset.id })),
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(MetricList);
