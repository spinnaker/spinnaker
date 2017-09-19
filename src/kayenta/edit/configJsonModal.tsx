import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Modal } from 'react-bootstrap';
import { get } from 'lodash';

import * as Creators from 'kayenta/actions/creators';
import { ICanaryState } from '../reducers/index';
import { jsonUtilityService, NgReact, IJsonDiff } from '@spinnaker/core';
import { mapStateToConfig } from '../service/canaryConfig.service';
import Styleguide from '../layout/styleguide';
import { Tab, Tabs } from '../layout/tabs';

const { DiffView } = NgReact;

import './configJson.less';

interface IConfigJsonDispatchProps {
  closeModal: () => void;
  setConfigJson: (event: React.ChangeEvent<HTMLTextAreaElement>) => void;
  updateConfig: (event: any) => void;
  setTabState: (state: ConfigJsonModalTabState) => () => void;
}

interface IConfigJsonStateProps {
  show: boolean;
  configJson: string;
  deserializationError: string;
  tabState: ConfigJsonModalTabState;
  diff: IJsonDiff;
}

export enum ConfigJsonModalTabState {
  Edit,
  Diff,
}

/*
 * Modal for viewing canary config JSON.
 */
function ConfigJsonModal({ show, configJson, deserializationError, closeModal, setConfigJson, updateConfig, setTabState, tabState, diff }: IConfigJsonDispatchProps & IConfigJsonStateProps) {
  return (
    <Modal show={show} onHide={onHide} bsSize="large">
      <Styleguide>
        <Modal.Header>
          <Modal.Title>JSON</Modal.Title>
        </Modal.Header>
          <Modal.Body>
            <section>
              <Tabs>
                <Tab selected={tabState === ConfigJsonModalTabState.Edit}>
                  <a onClick={setTabState(ConfigJsonModalTabState.Edit)}>Edit</a>
                </Tab>
                <Tab selected={tabState === ConfigJsonModalTabState.Diff}>
                  <a onClick={setTabState(ConfigJsonModalTabState.Diff)}>Diff</a>
                </Tab>
              </Tabs>
            </section>
            <section className="kayenta-config-json">
              {tabState === ConfigJsonModalTabState.Edit && (
                <textarea
                  rows={configJson.split('\n').length}
                  className="form-control code flex-fill"
                  spellCheck={false}
                  value={configJson}
                  onChange={setConfigJson}
                />
              )}
              {tabState === ConfigJsonModalTabState.Diff && !deserializationError && (
                <div className="modal-show-history">
                  <div className="show-history">
                    <DiffView diff={diff}/>
                  </div>
                </div>
              )}
              {!!deserializationError && (
                <div className="horizontal row center">
                  <span className="error-message">Error: {deserializationError}</span>
                </div>
              )}
            </section>
          </Modal.Body>
        <Modal.Footer>
          <ul className="list-inline pull-right">
            <li>
              <button className="passive" onClick={closeModal}>Close</button>
            </li>
            <li>
              <button
                className="primary"
                data-serialized={configJson}
                onClick={updateConfig}
                disabled={!!deserializationError}
              >Update
              </button>
            </li>
          </ul>
        </Modal.Footer>
      </Styleguide>
    </Modal>
  );
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IConfigJsonDispatchProps {
  return {
    closeModal: () => dispatch(Creators.closeConfigJsonModal()),
    setTabState: (state: ConfigJsonModalTabState) => () => dispatch(Creators.setConfigJsonModalTabState({ state })),
    setConfigJson: (event: React.ChangeEvent<HTMLTextAreaElement>) => dispatch(Creators.setConfigJson({ json: event.target.value })),
    updateConfig: (event: any) => {
      dispatch(Creators.selectConfig({ config: JSON.parse(event.target.dataset.serialized) }));
    }
  };
}

function mapStateToProps(state: ICanaryState): IConfigJsonStateProps {
  const persistedConfig = jsonUtilityService.makeSortedStringFromObject(
    state.data.configs.find(c => c.name === get(state, 'selectedConfig.config.name')) || {}
  );

  const configJson = state.selectedConfig.json.configJson || jsonUtilityService.makeSortedStringFromObject(mapStateToConfig(state) || {});

  return {
    configJson,
    show: state.app.configJsonModalOpen,
    deserializationError: state.selectedConfig.json.error,
    tabState: state.app.configJsonModalTabState,
    diff: state.selectedConfig.json.error ? null : jsonUtilityService.diff(persistedConfig, configJson, true),
  };
}

const onHide = (): void => null;

export default connect(mapStateToProps, mapDispatchToProps)(ConfigJsonModal);
