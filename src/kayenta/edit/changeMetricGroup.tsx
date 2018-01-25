import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import * as Select from 'react-select';

import { HoverablePopover } from '@spinnaker/core';

import * as Creators from '../actions/creators';
import { ICanaryState } from '../reducers/index';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { UNGROUPED } from './groupTabs';
import Styleguide from '../layout/styleguide';

import './changeMetricGroup.less';

interface IChangeMetricGroupStateProps {
  groups: Select.Option[];
  toGroup: string;
}

export interface IChangeMetricGroupOwnProps {
  metric: ICanaryMetricConfig;
}

interface IChangeMetricGroupDispatchProps {
  select: (selected: Select.Option) => void;
  clear: () => void;
  confirm: () => void;
}

function ChangeMetricGroup({ groups, toGroup, confirm, clear, select }: IChangeMetricGroupStateProps & IChangeMetricGroupDispatchProps) {
  const template = (
    <Styleguide>
      <section className="horizontal">
        <Select
          value={toGroup}
          options={groups}
          clearable={false}
          onChange={select}
          className="flex-6 select"
        />
        <button className="primary flex-1" disabled={!toGroup} onClick={confirm}>OK</button>
      </section>
    </Styleguide>
  );

  return (
    <HoverablePopover onShow={clear} title="Move to:" className="change-metric-group" template={template}>
      <i className="fa fa-folder-o"/>
    </HoverablePopover>
  );
}

function mapStateToProps(state: ICanaryState, { metric }: IChangeMetricGroupOwnProps): IChangeMetricGroupStateProps {
  // If a metric belongs to more than one group, allow a move into one of those groups.
  // e.g., a [system, requests] -> [requests] move should be allowed, but
  // don't offer a [system] -> [system] move.
  const groupNames = state.selectedConfig.group.list.filter(g => metric.groups.length > 1 || !metric.groups.includes(g));
  const isGroupedMetric = !!metric.groups.length;

  let groups = groupNames.map(g => ({ label: g, value: g }));
  if (isGroupedMetric) {
    groups = groups.concat([{ label: UNGROUPED, value: UNGROUPED }]);
  }
  return {
    groups,
    toGroup: state.selectedConfig.changeMetricGroup.toGroup,
  }
}

function mapDispatchToProps(dispatch: (action: Action & any) => void, { metric }: IChangeMetricGroupOwnProps): IChangeMetricGroupDispatchProps {
  return {
    select: (selected: Select.Option) => {
      dispatch(Creators.changeMetricGroupSelect({ group: selected.value as string }));
    },
    clear: () => {
      dispatch(Creators.changeMetricGroupSelect({ group: null }));
    },
    confirm: () => {
      dispatch(Creators.changeMetricGroupConfirm({ metricId: metric.id }));
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(ChangeMetricGroup);
