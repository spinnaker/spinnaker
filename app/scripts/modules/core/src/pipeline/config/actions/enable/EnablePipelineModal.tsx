import { get } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { IPipeline } from '../../../../domain';
import { ModalClose } from '../../../../modal';
import { IModalComponentProps } from '../../../../presentation';

import { PipelineConfigService } from '../../services/PipelineConfigService';

export interface IEnablePipelineModalProps extends IModalComponentProps {
  pipeline: IPipeline;
}

export function EnablePipelineModal(props: IEnablePipelineModalProps) {
  const [errorMessage, setErrorMessage] = React.useState<string>(null);
  const [saveError, setSaveError] = React.useState<boolean>(false);
  const { closeModal, dismissModal, pipeline } = props;

  function enablePipeline() {
    PipelineConfigService.savePipeline({ ...pipeline, disabled: false }).then(
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
          <h3>Really Enable Pipeline?</h3>
        </Modal.Header>
        <Modal.Body>
          {saveError && (
            <div className="alert alert-danger">
              <p>Could not enable pipeline.</p>
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
                <p>Are you sure you want to enable {pipeline.name}?</p>
              </div>
            </div>
          </form>
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" onClick={dismissModal} type="button">
            Cancel
          </button>
          <button className="btn btn-primary" onClick={enablePipeline} type="button">
            Enable pipeline
          </button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
