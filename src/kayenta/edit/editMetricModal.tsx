import * as React from 'react';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';

import * as Creators from '../actions/creators';
import {ICanaryState} from '../reducers/index';
import {ICanaryMetricConfig} from 'kayenta/domain';
import MetricConfigurerDelegator from './metricConfigurerDelegator';
import Styleguide from '../layout/styleguide';
import FormRow from '../layout/formRow';
import KayentaInput from '../layout/kayentaInput';

import './editMetricModal.less';

interface IEditMetricModalDispatchProps {
  rename: (event: any) => void;
  updateDirection: (event: any) => void;
  confirm: () => void;
  cancel: () => void;
}

interface IEditMetricModalStateProps {
  metric: ICanaryMetricConfig
}

function DirectionChoice({ value, label, current, action }: { value: string, label: string, current: string, action: (event: any) => void }) {
  return (
    <label style={{fontWeight: 'normal'}}>
      <input name="direction" type="radio" value={value} onClick={action} checked={value === current}/> {label} &nbsp;
    </label>
  );
}

/*
 * Modal to edit metric details.
 */
function EditMetricModal({ metric, rename, confirm, cancel, updateDirection }: IEditMetricModalDispatchProps & IEditMetricModalStateProps) {
  if (!metric) {
    return null;
  }

  const direction = metric.classificationDirection || 'Either';
  return (
    <Modal show={true} onHide={noop} className="kayenta-edit-metric-modal">
      <Styleguide>
        <Modal.Header>
          <Modal.Title>Configure Metric</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <FormRow label="Name">
            <KayentaInput
              type="text"
              value={metric.name}
              data-id={metric.id}
              onChange={rename}
            />
          </FormRow>
          <FormRow label="Fail on">
            <DirectionChoice value="Increase" label="increase" current={direction} action={updateDirection}/>
            <DirectionChoice value="Decrease" label="decrease" current={direction} action={updateDirection}/>
            <DirectionChoice value="Either"   label="either"   current={direction} action={updateDirection}/>
          </FormRow>
          <MetricConfigurerDelegator/>
        </Modal.Body>
        <Modal.Footer>
          <ul className="list-inline pull-right">
            <li><button className="passive" onClick={cancel}>Cancel</button></li>
            <li><button className="primary" onClick={confirm}>OK</button></li>
          </ul>
        </Modal.Footer>
      </Styleguide>
    </Modal>
  );
}

function mapDispatchToProps(dispatch: any): IEditMetricModalDispatchProps {
  return {
    rename: (event: any) => {
      dispatch(Creators.renameMetric({ id: event.target.dataset.id, name: event.target.value }));
    },
    cancel: () => {
      dispatch(Creators.editMetricCancel());
    },
    confirm: () => {
      dispatch(Creators.editMetricConfirm());
    },
    updateDirection: (event: any) => {
      dispatch(Creators.updateMetricDirection({ id: event.target.dataset.id, direction: event.target.value }))
    },
  };
}

function mapStateToProps(state: ICanaryState): IEditMetricModalStateProps {
  return {
    metric: state.selectedConfig.editingMetric,
  };
}

// For onHide, above. Not sure how to avoid this.
const noop = (): void => null;

export default connect(mapStateToProps, mapDispatchToProps)(EditMetricModal);
