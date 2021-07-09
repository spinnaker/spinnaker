import 'brace/mode/json';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { IPipeline, IPipelineLock, IStage } from '../../../../domain';
import { IModalComponentProps, JsonEditor } from '../../../../presentation';
import { PipelineJSONService } from '../../services/pipelineJSON.service';
import { JsonUtils, noop } from '../../../../utils';

export interface IEditPipelineJsonModalProps extends IModalComponentProps {
  pipeline: IPipeline;
  plan: IPipeline;
}

export interface IEditPipelineJsonModalState {
  errorMessage?: string;
  pipelineJSON: string;
  pipelinePlanJSON?: string;
  locked: IPipelineLock;
  isStrategy: boolean;
  activeTab: mode;
}

type mode = 'pipeline' | 'renderedPipeline';

export class EditPipelineJsonModal extends React.Component<IEditPipelineJsonModalProps, IEditPipelineJsonModalState> {
  public static defaultProps: Partial<IEditPipelineJsonModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  constructor(props: IEditPipelineJsonModalProps) {
    super(props);

    const copy = PipelineJSONService.clone(props.pipeline);
    let copyPlan: IPipeline;
    if (props.plan) {
      copyPlan = PipelineJSONService.clone(props.plan);
    }

    this.state = {
      pipelineJSON: JsonUtils.makeSortedStringFromObject(copy),
      pipelinePlanJSON: copyPlan ? JsonUtils.makeSortedStringFromObject(copyPlan) : null,
      locked: copy.locked,
      isStrategy: copy.strategy || false,
      activeTab: 'pipeline',
    };
  }

  private removeImmutableFields(pipeline: IPipeline): void {
    // no index signature on pipeline
    PipelineJSONService.immutableFields.forEach((k) => delete (pipeline as any)[k]);
  }

  private validatePipeline(pipeline: IPipeline): void {
    const refIds = new Set<string | number>();
    const badIds = new Set<string | number>();
    if (pipeline.stages) {
      pipeline.stages.forEach((stage: IStage) => {
        if (refIds.has(stage.refId)) {
          badIds.add(stage.refId);
        }
        refIds.add(stage.refId);
      });

      if (badIds.size) {
        throw new Error(
          `The refId property must be unique across stages.  Duplicate id(s): ${Array.from(badIds).toString()}`,
        );
      }
    }
  }

  private updatePipeline = (): void => {
    const { pipelineJSON } = this.state;
    const { pipeline } = this.props;

    try {
      const parsed = JSON.parse(pipelineJSON);
      parsed.appConfig = parsed.appConfig || {};

      this.validatePipeline(parsed);

      Object.keys(pipeline)
        .filter((k) => !PipelineJSONService.immutableFields.has(k) && !parsed.hasOwnProperty(k))
        .forEach((k) => delete (pipeline as any)[k]);
      this.removeImmutableFields(parsed);
      Object.assign(pipeline, parsed);

      this.props.closeModal();
    } catch (e) {
      this.setState({ errorMessage: e.message });
    }
  };

  private setActiveTab(activeTab: mode) {
    this.setState({ activeTab });
  }

  private updateJson = (pipelineJSON: string) => {
    this.setState({ pipelineJSON });
  };

  private onValidate = (errorMessage: string) => {
    this.setState({ errorMessage });
  };

  public render() {
    const { pipelineJSON, pipelinePlanJSON, locked, isStrategy, activeTab, errorMessage } = this.state;
    const invalid = !!errorMessage;
    const { dismissModal } = this.props;

    return (
      <>
        <Modal.Header>
          <Modal.Title>
            {!locked && <span>Edit </span>}
            {isStrategy ? 'Strategy' : 'Pipeline'} JSON
          </Modal.Title>
        </Modal.Header>
        <Modal.Body className="flex-fill">
          {!!pipelinePlanJSON && (
            <ul className="tabs-basic" style={{ listStyle: 'none' }}>
              <li role="presentation" className={activeTab === 'pipeline' ? 'selected' : ''}>
                <a onClick={() => this.setActiveTab('pipeline')}>Configuration</a>
              </li>
              <li role="presentation" className={activeTab === 'renderedPipeline' ? 'selected' : ''}>
                <a onClick={() => this.setActiveTab('renderedPipeline')}>Rendered pipeline</a>
              </li>
            </ul>
          )}
          {activeTab === 'pipeline' && (
            <>
              <p>
                The JSON below represents the {isStrategy ? 'strategy' : 'pipeline'} configuration in its persisted
                state.
              </p>
              {!locked && (
                <p>
                  <strong>Note:</strong> Clicking "Update {isStrategy ? 'Strategy' : 'Pipeline'}" below will not save
                  your changes to the server - it only updates the configuration within the browser, so you'll want to
                  verify your changes and click "Save Changes" when you're ready.
                </p>
              )}
              <JsonEditor value={pipelineJSON} onChange={this.updateJson} onValidation={this.onValidate} />
            </>
          )}
          {activeTab === 'renderedPipeline' && (
            <>
              <p>This pipeline is based on a template. The JSON below represents the rendered pipeline.</p>
              <form role="form" name="form" className="form-horizontal flex-fill">
                <div className="flex-fill">
                  <JsonEditor value={pipelinePlanJSON} readOnly={true} />
                </div>
              </form>
            </>
          )}
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
          {!locked && (
            <button disabled={invalid} className="btn btn-primary" onClick={this.updatePipeline}>
              <span className="far fa-check-circle" /> Update {isStrategy ? 'Strategy' : 'Pipeline'}
            </button>
          )}
        </Modal.Footer>
      </>
    );
  }
}
