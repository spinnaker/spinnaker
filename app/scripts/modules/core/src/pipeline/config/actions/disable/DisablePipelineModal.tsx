import { get } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { IPipeline } from '../../../../domain';
import { ModalClose } from '../../../../modal';
import { IModalComponentProps } from '../../../../presentation';

import { PipelineConfigService } from '../../services/PipelineConfigService';

export interface IDisablePipelineModalProps extends IModalComponentProps {
  pipeline: IPipeline;
}

export function DisablePipelineModal(props: IDisablePipelineModalProps) {
  const [errorMessage, setErrorMessage] = React.useState<string>(null);
  const [saveError, setSaveError] = React.useState<boolean>(false);
  const { closeModal, dismissModal, pipeline } = props;

  function disablePipeline() {
    PipelineConfigService.savePipeline({ ...pipeline, disabled: true }).then(
      () => closeModal(),
      (response) => {
        setSaveError(true);
        setErrorMessage(get(response, 'data.message', 'No message provided'));
      },
    );
  }

  return (
    <>
      <Modal key="modal" show={true} onHide={() => {}}>
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>Really Disable Pipeline?</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {saveError && (
            <div className="alert alert-danger">
              <p>Could not disable pipeline.</p>
              <p>
                <b>Reason: </b>
                {errorMessage}
              </p>
              <p>
                <a
                  className="btn btn-link"
                  onClick={(e) => {
                    e.preventDefault();
                    setSaveError(false);
                  }}
                >
                  [dismiss]
                </a>
              </p>
            </div>
          )}
          <form role="form" name="form" className="form-horizontal">
            <div className="form-group">
              <div className="col-md-12">
                <p>Are you sure you want to disable {pipeline.name}?</p>
                <p>
                  This will prevent any triggers from firing, and will also prevent users from running the pipeline
                  manually.
                </p>
              </div>
            </div>
          </form>
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" onClick={dismissModal} type="button">
            Cancel
          </button>
          <button className="btn btn-primary" onClick={disablePipeline} type="button">
            Disable pipeline
          </button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
