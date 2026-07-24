import React, { useEffect, useState } from 'react';

import { ReactInjector } from '../../../../../reactShims';

function CancelAllModal(props: any) {
  const [loading, setLoading] = useState('none');
  const [isDisabled, setDisabled] = useState(false);
  const [autoCloseModal, setAutoCloseModal] = useState(false);

  useEffect(() => {
    if (autoCloseModal === true) {
      props.setOpenModal(false);
    }
  }, [autoCloseModal]);

  const handleCancel = async () => {
    setLoading('block');
    setDisabled(true);
    for (const execution of props.allRunning) {
      ReactInjector.executionService
        .cancelExecution(props.application, execution.id)
        .then((response) => {
          return response;
        })
        .catch((e) => {
          return e;
          //it always goes to catch even when cancel its executed correctly
        });
    }
    await new Promise((f) => setTimeout(f, 1000));
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
                <h4 className="modal-title">Really stop execution of all apps?</h4>
              </div>
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
                <button onClick={handleCancel} className="btn btn-primary" type="button" disabled={isDisabled}>
                  <div className="flex-container-h horizontal middle">
                    {loading != 'none' && <i className="fa fa-spinner fa-spin" />}
                    {loading == 'none' && <i className="far fa-check-circle"></i>}
                    <span className="sp-margin-xs-left">Stop all pipelines</span>
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

export default CancelAllModal;
