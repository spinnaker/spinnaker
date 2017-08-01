import * as React from 'react';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';

import { SubmitButton } from '@spinnaker/core';

import {
  EDIT_METRIC_CONFIRM,
  EDIT_METRIC_CANCEL,
  RENAME_METRIC
} from '../actions/index';
import {ICanaryState} from '../reducers/index';
import {ICanaryMetricConfig} from 'kayenta/domain';

interface IEditMetricModalDispatchProps {
  rename: (event: any) => void;
  confirm: () => void;
  cancel: () => void;
}

interface IEditMetricModalStateProps {
  metric: ICanaryMetricConfig
}

/*
 * Modal to edit metric details.
 */
function EditMetricModal({ metric, rename, confirm, cancel }: IEditMetricModalDispatchProps & IEditMetricModalStateProps) {
  if (!metric) {
    return null;
  }
  return (
    <Modal show={true} onHide={null}>
      <Modal.Header>
        <Modal.Title>Configure Metric</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <label>Name:</label>
        <input
          type="text"
          value={metric.name}
          data-id={metric.id}
          onChange={rename}
        />
      </Modal.Body>
      <Modal.Footer>
        <button onClick={cancel}>Cancel</button>
        <SubmitButton
          label="OK"
          submitting={false}
          onClick={confirm}
        />
      </Modal.Footer>
    </Modal>
  );
}

function mapDispatchToProps(dispatch: any): IEditMetricModalDispatchProps {
  return {
    rename: (event: any) => {
      dispatch({ type: RENAME_METRIC, id: event.target.dataset.id, name: event.target.value });
    },
    cancel: () => {
      dispatch({ type: EDIT_METRIC_CANCEL });
    },
    confirm: () => {
      dispatch({ type: EDIT_METRIC_CONFIRM })
    },
  };
}

function mapStateToProps(state: ICanaryState): IEditMetricModalStateProps {
  return {
    metric: state.editingMetric
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(EditMetricModal);
