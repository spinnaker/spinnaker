import React from 'react';

import type { IModalComponentProps } from '@spinnaker/core';
import { ModalClose, noop, ReactModal, REST } from '@spinnaker/core';

export interface IGenerateScoreModalProps extends IModalComponentProps {
  canaryId: string;
}

export function GenerateScoreModal({ canaryId, dismissModal }: IGenerateScoreModalProps) {
  const [command, setCommand] = React.useState({ duration: null as number, durationUnit: 'h' });
  const [state, setState] = React.useState('editing');

  const generateCanaryScore = () => {
    setState('submitting');
    REST('/canaries')
      .path(canaryId, 'generateCanaryResult')
      .post(command)
      .then(() => setState('success'))
      .catch(() => setState('error'));
  };

  return (
    <form role="form" onSubmit={(e) => e.preventDefault()}>
      <ModalClose dismiss={dismissModal} />
      <div className="modal-header">
        <h4 className="modal-title">Generate Canary Analysis Result</h4>
      </div>
      <div className="modal-body container-fluid form-inline">
        <div className="form-group">
          <label> Generate a score for the past </label>{' '}
          <input
            style={{ width: 60 }}
            type="number"
            className="form-control input-sm"
            min="0"
            required={true}
            value={command.duration || ''}
            onChange={(e) => setCommand({ ...command, duration: Number(e.target.value) })}
          />{' '}
          <select
            style={{ width: 90 }}
            className="form-control input-sm"
            value={command.durationUnit}
            onChange={(e) => setCommand({ ...command, durationUnit: e.target.value })}
          >
            <option value="h">hours</option>
            <option value="m">minutes</option>
          </select>
        </div>
        {state === 'success' && (
          <div className="form-group" style={{ marginTop: 30 }}>
            <div className="alert alert-success">
              <p>
                <span className="far fa-check-circle" /> Successfully requested a new canary analysis
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
            onClick={generateCanaryScore}
            disabled={state === 'submitting' || !command.duration || command.duration < 1}
          >
            {state === 'submitting' && <span className="glyphicon glyphicon-refresh spinning" />} Submit
          </button>
        )}
      </div>
    </form>
  );
}

GenerateScoreModal.defaultProps = {
  dismissModal: noop,
};

GenerateScoreModal.show = (props: IGenerateScoreModalProps) => ReactModal.show(GenerateScoreModal, props);
