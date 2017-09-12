import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';

import {
  CONFIG_JSON_DESERIALIZATION_ERROR,
  SELECT_CONFIG,
  SET_CONFIG_JSON
} from '../actions/index';
import * as Creators from 'kayenta/actions/creators';
import { ICanaryState } from '../reducers/index';
import { ICanaryConfig } from '../domain/ICanaryConfig';
import { jsonUtilityService } from '@spinnaker/core';
import { mapStateToConfig } from '../service/canaryConfig.service';
import Styleguide from '../layout/styleguide';

interface IEditConfigJsonDispatchProps {
  closeModal: () => void;
  setConfigJson: (event: React.ChangeEvent<HTMLTextAreaElement>) => void;
  tryToUpdateConfig: (event: any) => void;
}

interface IEditConfigJsonStateProps {
  show: boolean;
  selectedConfig: ICanaryConfig;
  configJson: string;
  deserializationError: string;
}

/*
 * Modal for editing canary config JSON.
 */
function EditConfigJsonModal({ show, selectedConfig, configJson, deserializationError, closeModal, setConfigJson, tryToUpdateConfig }: IEditConfigJsonDispatchProps & IEditConfigJsonStateProps) {
  if (!configJson) {
    configJson = jsonUtilityService.makeSortedStringFromObject(selectedConfig);
  }

  return (
    <Modal show={show} onHide={null}>
      <Styleguide>
        <Modal.Header>
          <Modal.Title>Edit Config</Modal.Title>
        </Modal.Header>
          <Modal.Body>
            <textarea
              rows={configJson.split('\n').length}
              className="form-control code flex-fill"
              spellCheck={false}
              value={configJson}
              onChange={setConfigJson}
            />
            {!!deserializationError && (
              <div className="form-group row slide-in">
                <div className="col-sm-9 col-sm-offset-3 error-message">
                  Error: {deserializationError}
                </div>
              </div>
            )}
          </Modal.Body>
        <Modal.Footer>
          <ul className="list-inline pull-right">
            <li><button className="passive" onClick={closeModal}>Cancel</button></li>
            <li><button className="primary" data-serialized={configJson} onClick={tryToUpdateConfig}>Update</button></li>
          </ul>
        </Modal.Footer>
      </Styleguide>
    </Modal>
  );
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IEditConfigJsonDispatchProps {
  return {
    closeModal: () => dispatch(Creators.closeEditConfigJsonModal()),
    setConfigJson: (event: React.ChangeEvent<HTMLTextAreaElement>) => {
      dispatch({
        type: SET_CONFIG_JSON,
        configJson: event.target.value
      });
    },
    tryToUpdateConfig: (event: any) => {
      try {
        dispatch({
          type: SELECT_CONFIG,
          config: JSON.parse(event.target.dataset.serialized),
        });
      } catch (error) {
        dispatch({
          type: CONFIG_JSON_DESERIALIZATION_ERROR,
          error: error.message,
        });
      }
    }
  };
}

function mapStateToProps(state: ICanaryState): IEditConfigJsonStateProps {
  return {
    show: state.app.editConfigJsonModalOpen,
    selectedConfig: mapStateToConfig(state),
    configJson: state.selectedConfig.json.state,
    deserializationError: state.selectedConfig.json.error,
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(EditConfigJsonModal);
