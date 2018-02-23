import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';

import { noop } from '@spinnaker/core';

import * as Creators from '../actions/creators';
import { ICanaryState } from '../reducers/index';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';
import Styleguide from '../layout/styleguide';

interface IChangeMetricGroupModalOwnProps {
  metric: ICanaryMetricConfig;
}

interface IChangeMetricGroupModalStateProps {
  groups: string[];
  toGroup: string;
}

interface IChangeMetricGroupModalDispatchProps {
  select: (event: any) => void;
  clear: () => void;
  confirm: () => void;
}

function ChangeMetricGroupModal({ groups, toGroup, confirm, clear, select }: IChangeMetricGroupModalStateProps & IChangeMetricGroupModalDispatchProps) {
  return (
    <Modal show={true} onHide={noop}>
      <Styleguide>
        <Modal.Header>
          <Modal.Title>Change Metric Group</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <select value={toGroup || ''} onChange={select} className="form-control input-sm">
            <option value={''}>-- select group --</option>
            {
              groups.map(g => (
                <option key={g} value={g}>{g}</option>
              ))
            }
          </select>
        </Modal.Body>
        <Modal.Footer>
          <ul className="list-inline pull-right">
            <li>
              <button className="passive" onClick={clear}>Cancel</button>
            </li>
            <li>
              <button className="primary" disabled={!toGroup} onClick={confirm}>OK</button>
            </li>
          </ul>
        </Modal.Footer>
      </Styleguide>
    </Modal>
  );
}

function mapStateToProps(state: ICanaryState, { metric }: IChangeMetricGroupModalOwnProps): IChangeMetricGroupModalStateProps {
  // If a metric belongs to more than one group, allow a move into one of those groups.
  // e.g., a [system, requests] -> [requests] move should be allowed, but
  // don't offer a [system] -> [system] move.
  return {
    groups: state.selectedConfig.group.list.filter(g => metric.groups.length > 1 || !metric.groups.includes(g)),
    toGroup: state.selectedConfig.changeMetricGroup.toGroup,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void, { metric }: IChangeMetricGroupModalOwnProps): IChangeMetricGroupModalDispatchProps {
  return {
    select: (event: any) => {
      dispatch(Creators.changeMetricGroupSelect({ group: event.target.value || null }));
    },
    clear: () => {
      dispatch(Creators.changeMetricGroupConfirm({ metricId: null }));
    },
    confirm: () => {
      dispatch(Creators.changeMetricGroupConfirm({ metricId: metric.id }));
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(ChangeMetricGroupModal);
