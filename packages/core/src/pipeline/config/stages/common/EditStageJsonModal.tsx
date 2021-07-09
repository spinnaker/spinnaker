import 'brace/mode/json';
import { cloneDeepWith } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { IStage } from '../../../../domain';
import { IModalComponentProps, JsonEditor } from '../../../../presentation';
import { JsonUtils, noop } from '../../../../utils';

export interface IEditStageJsonModalProps extends IModalComponentProps {
  stage: IStage;
}

export interface IEditStageJsonModalState {
  errorMessage?: string;
  stageJSON: string;
}

export class EditStageJsonModal extends React.Component<IEditStageJsonModalProps, IEditStageJsonModalState> {
  public static defaultProps: Partial<IEditStageJsonModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private immutableFields = ['$$hashKey', 'refId', 'requisiteStageRefIds'];

  constructor(props: IEditStageJsonModalProps) {
    super(props);
    const copy = cloneDeepWith<IStage>(props.stage, (value: any) => {
      if (value && value.$$hashKey) {
        delete value.$$hashKey;
      }
      return undefined; // required for clone operation and typescript happiness
    });
    this.immutableFields.forEach((k) => delete copy[k]);

    this.state = {
      stageJSON: JsonUtils.makeSortedStringFromObject(copy),
    };
  }

  private updateStage() {
    const { stageJSON } = this.state;
    const { stage } = this.props;

    try {
      const parsed = JSON.parse(stageJSON);

      Object.keys(stage)
        .filter((k) => !this.immutableFields.includes(k))
        .forEach((k) => delete stage[k]);
      Object.assign(stage, parsed);

      this.props.closeModal();
    } catch (e) {
      this.setState({ errorMessage: e.message });
    }
  }

  private onValidate = (errorMessage: string) => {
    this.setState({ errorMessage });
  };

  private updateJson = (stageJSON: string) => {
    this.setState({ stageJSON });
  };

  public render() {
    const { stageJSON, errorMessage } = this.state;
    const invalid = !!errorMessage;
    const { dismissModal } = this.props;
    return (
      <>
        <Modal.Header>
          <Modal.Title>Edit Stage JSON</Modal.Title>
        </Modal.Header>
        <Modal.Body className="flex-fill">
          <p>The JSON below represents the stage configuration in its persisted state.</p>
          <form name="form" className="form-horizontal flex-fill">
            <div className="flex-fill">
              <JsonEditor value={stageJSON} onChange={this.updateJson} onValidation={this.onValidate} />
            </div>
          </form>
          <p>
            <strong>Note:</strong> Clicking "Update Stage" below will not save your changes to the server - it only
            updates the configuration within the browser, so you'll want to verify your changes and click "Save Changes"
            when you're ready.
          </p>
        </Modal.Body>
        <Modal.Footer>
          {invalid && (
            <div className="slide-in">
              <div className="error-message">Error: {errorMessage}</div>
            </div>
          )}
          <button className="btn btn-default" onClick={() => dismissModal()}>
            Cancel
          </button>
          <button className="btn btn-primary" disabled={invalid} onClick={() => this.updateStage()}>
            <span className="far fa-check-circle" /> Update Stage
          </button>
        </Modal.Footer>
      </>
    );
  }
}
