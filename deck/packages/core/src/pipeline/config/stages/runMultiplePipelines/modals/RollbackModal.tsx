import React, { useEffect, useState } from 'react';

import type { IPipeline, IStage } from '../../../../../domain';
import { PipelineConfigService } from '../../../services/PipelineConfigService';

function RollbackModal(props: any) {
  let rollbackPipelineId = '';
  const parameterAppName = props.executionData.executionIdentifier;
  let account = '';
  let manifestName = '';
  let location = '';

  const [error, setError] = useState('');
  const [loading, setLoading] = useState('none');
  const [isDisabled, setDisabled] = useState(false);
  const [autoCloseModal, setAutoCloseModal] = useState(false);

  useEffect(() => {
    if (autoCloseModal === true && error === '') {
      props.setOpenModal(false);
    }
  }, [error, autoCloseModal]);

  (function () {
    if (props.executionData.artifactCreated === undefined) {
      if (error === '') {
        setError("Can't perform rollback this pipeline did not create an artifact");
      }
      return;
    }
    account = props.executionData.artifactCreated.account;
    manifestName = props.executionData.artifactCreated.manifestName;
    location = props.executionData.artifactCreated.location;

    if (error === '' && rollbackPipelineId === '') {
      const pipelines = props.application.getDataSource('pipelineConfigs').data;
      const foundRollbackOnFailure = pipelines.find((pipeline: any) => pipeline.name === 'rollbackOnFailure');
      if (foundRollbackOnFailure === undefined) {
        setError('Pipeline "rollbackOnFailure" not found create a pipeline with that name');
      } else {
        rollbackPipelineId = foundRollbackOnFailure.id;
      }
    }
  })();

  const stage: IStage = {
    account: account,
    manifestName: manifestName,
    location: location,
    numRevisionsBack: 1,
    cloudProvider: 'kubernetes',
    mode: 'static',
    name: 'Undo Rollout (Manifest) ' + parameterAppName,
    refId: '2', // unfortunately, we kept this loose early on, so it's either a string or a number
    requisiteStageRefIds: ['1'],
    type: 'undoRolloutManifest',
  };

  const preconditionStage: IStage = {
    preconditions: [
      {
        context: {
          expression: "${trigger['id']!=null}",
          failureMessage:
            'This pipeline cannot be run manually. Please perform rollback using the bundled deployment pipeline.',
        },
        failPipeline: true,
        type: 'expression',
      },
    ],
    name: 'Check Preconditions',
    refId: '1',
    requisiteStageRefIds: [],
    type: 'checkPreconditions',
  };

  const handleRollback = async () => {
    setLoading('block');
    setDisabled(true);
    if (error === '') {
      const stagesArray = [preconditionStage, stage];
      const pipeline: IPipeline = {
        application: props.application.name,
        id: rollbackPipelineId,
        keepWaitingPipelines: false,
        limitConcurrent: false,
        name: 'rollbackOnFailure',
        stages: stagesArray,
        triggers: [],
        parameterConfig: [],
      };

      let triggerAfterSave = false;
      await PipelineConfigService.savePipeline(pipeline)
        .then((response) => {
          triggerAfterSave = true;
          return response;
        })
        .catch((e) => {
          if (e.data.message == 'null') {
            setError('No details provided.');
          } else {
            setError(e.data.message);
          }
        });

      if (triggerAfterSave) {
        await PipelineConfigService.triggerPipeline(props.application.name, 'rollbackOnFailure', {
          id: rollbackPipelineId,
        })
          .then((response) => {
            return response;
          })
          .catch((e) => {
            if (e.data.message == 'null') {
              setError('No details provided.');
            } else {
              setError(e.data.message);
            }
          });
      }
    }
    setAutoCloseModal(true);
  };

  return (
    <div role="dialog">
      <div className="fade modal-backdrop in"></div>
      <div role="dialog" className="fade in modal" style={{ display: 'block' }}>
        <div className="modal-dialog">
          <div className="modal-content" role="document">
            <form className="form-horizontal">
              <div className="modal-close close-button pull-right" style={{ marginTop: '4px', marginRight: '4px' }}>
                <button
                  onClick={() => {
                    props.setOpenModal(false);
                  }}
                  className="link"
                  type="button"
                >
                  <span className="glyphicon glyphicon-remove"></span>
                </button>
              </div>
              <div className="modal-header">
                <h4 className="modal-title">Really perform Rollback of {parameterAppName}?</h4>
              </div>
              {error === '' && (
                <div className="modal-body">
                  <p>This will perform rollback for the artifact created on {parameterAppName} Deploy stage.</p>
                </div>
              )}
              {error != '' && (
                <div className="modal-body" style={{ color: '#bb231e' }}>
                  <h4>Error can not rollback</h4>
                  <p>{error}</p>
                </div>
              )}
              <div className="modal-footer">
                <button
                  onClick={() => {
                    props.setOpenModal(false);
                  }}
                  className="btn btn-default"
                  disabled={isDisabled}
                  type="button"
                >
                  Cancel
                </button>
                <button onClick={handleRollback} className="btn btn-primary" type="button" disabled={isDisabled}>
                  <div className="flex-container-h horizontal middle">
                    {loading != 'none' && <i className="fa fa-spinner fa-spin" />}
                    {loading == 'none' && <i className="far fa-check-circle"></i>}
                    <span className="sp-margin-xs-left">Rollback</span>
                  </div>
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

export default RollbackModal;
