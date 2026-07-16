import React from 'react';

import type { IModalComponentProps } from '@spinnaker/core';
import { ModalClose, noop, ReactModal, REST } from '@spinnaker/core';

export interface IEndCanaryModalProps extends IModalComponentProps {
  canaryId: string;
}

export function EndCanaryModal({ canaryId, dismissModal }: IEndCanaryModalProps) {
  const [command, setCommand] = React.useState({ reason: '', result: 'SUCCESS' });
  const [state, setState] = React.useState('editing');

  const endCanary = () => {
    setState('submitting');
    REST('/canaries')
      .path(canaryId, 'end')
      .put(command)
      .then(() => setState('success'))
      .catch(() => setState('error'));
  };

  return (
    <form role="form" onSubmit={(e) => e.preventDefault()}>
      <ModalClose dismiss={dismissModal} />
      <div className="modal-header">
        <h4 className="modal-title">End Canary</h4>
      </div>
      <div className="modal-body container-fluid">
        <div className="form-group">
          <label>Set result to</label>
          <select
            className="form-control input-sm"
            value={command.result}
            onChange={(e) => setCommand({ ...command, result: e.target.value })}
          >
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILURE">FAILURE</option>
          </select>
        </div>
        <div className="form-group">
          <label>Reason for ending canary</label>
          <textarea
            className="form-control"
            rows={4}
            value={command.reason || ''}
            onChange={(e) => setCommand({ ...command, reason: e.target.value })}
          />
        </div>
        {state === 'success' && (
          <div className="form-group" style={{ marginTop: 30 }}>
            <div className="alert alert-success">
              <p>
                <span className="far fa-check-circle" /> Successfully requested canary completion
              </p>
            </div>
          </div>
        )}
        {state === 'error' && (
          <div className="form-group" style={{ marginTop: 30 }}>
            <div className="alert alert-danger">
              <p>There was an error with this request. Please try again later.</p>
            </div>
          </div>
        )}
      </div>
      <div className="modal-footer">
        <button type="button" className="btn btn-default" onClick={dismissModal}>
          {state === 'success' ? 'Close' : 'Cancel'}
        </button>
        {state !== 'success' && (
          <button
            type="submit"
            className="btn btn-primary"
            onClick={endCanary}
            disabled={state === 'submitting' || !command.reason}
          >
            {state === 'submitting' && <span className="glyphicon glyphicon-refresh spinning" />} Submit
          </button>
        )}
      </div>
    </form>
  );
}

EndCanaryModal.defaultProps = {
  dismissModal: noop,
};

EndCanaryModal.show = (props: IEndCanaryModalProps) => ReactModal.show(EndCanaryModal, props);
