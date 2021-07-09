import { get, isEmpty, set } from 'lodash';
import { $log } from 'ngimport';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { Application } from '../../../../application';
import { IPipeline } from '../../../../domain';
import { ModalClose } from '../../../../modal';
import { IModalComponentProps } from '../../../../presentation';
import { ReactInjector } from '../../../../reactShims';

import { PipelineConfigService } from '../../services/PipelineConfigService';

export interface IDeletePipelineModalProps extends IModalComponentProps {
  application: Application;
  pipeline: IPipeline;
}

export function DeletePipelineModal(props: IDeletePipelineModalProps) {
  const [errorMessage, setErrorMessage] = React.useState<string>(null);
  const [deleteError, setDeleteError] = React.useState<boolean>(false);
  const [deleting, setDeleting] = React.useState<boolean>(false);
  const { application, closeModal, dismissModal, pipeline } = props;

  function deletePipeline() {
    setDeleting(true);

    PipelineConfigService.deletePipeline(application.name, pipeline, pipeline.name).then(
      () => {
        const idsToUpdatedIndices = {};
        const isPipelineStrategy = pipeline.strategy === true;
        const data = isPipelineStrategy ? application.strategyConfigs.data : application.pipelineConfigs.data;
        data.splice(
          data.findIndex((p: any) => p.id === pipeline.id),
          1,
        );
        data.forEach((p: IPipeline, index: number) => {
          if (p.index !== index) {
            p.index = index;
            set(idsToUpdatedIndices, p.id, index);
          }
        });
        if (!isEmpty(idsToUpdatedIndices)) {
          PipelineConfigService.reorderPipelines(application.name, idsToUpdatedIndices, isPipelineStrategy);
        }
        ReactInjector.$state.go('^.executions', null, { location: 'replace' });
        closeModal();
      },
      (response) => {
        $log.warn(response);
        setDeleting(false);
        setDeleteError(true);
        setErrorMessage(get(response, 'data.message', 'No message provided'));
      },
    );
  }

  return (
    <>
      <Modal key="modal" show={true} onHide={() => {}}>
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>Really Delete {pipeline.strategy === true ? 'Strategy' : 'Pipeline'}?</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {deleteError && (
            <div className="alert alert-danger">
              <p>Could not delete {pipeline.strategy === true ? 'strategy' : 'pipeline'}.</p>
              <p>
                <b>Reason: </b>
                {errorMessage}
              </p>
              <p>
                <a
                  className="btn btn-link"
                  onClick={(e) => {
                    e.preventDefault();
                    setDeleteError(false);
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
                <p>
                  Are you sure you want to delete the {pipeline.strategy === true ? 'strategy: ' : 'pipeline: '}
                  {pipeline.name}?
                </p>
              </div>
            </div>
          </form>
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" onClick={dismissModal} type="button">
            Cancel
          </button>
          <button className="btn btn-primary" onClick={deletePipeline}>
            {!deleting && (
              <span>
                <span className="far fa-check-circle" /> Delete
              </span>
            )}
            {deleting && (
              <span className="pulsing">
                <span className="fa fa-cog fa-spin" /> Deleting&hellip;
              </span>
            )}
          </button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
