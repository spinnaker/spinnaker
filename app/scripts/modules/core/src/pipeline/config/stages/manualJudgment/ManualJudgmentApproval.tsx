import * as React from 'react';
import Select, { Option } from 'react-select';
import 'react-select/dist/react-select.css';

import { IExecution, IExecutionStage } from 'core/domain';
import { Application } from 'core/application/application.model';
import { Markdown } from 'core/presentation/Markdown';
import { NgReact, ReactInjector } from 'core/reactShims';

export interface IManualJudgmentApprovalProps {
  execution: IExecution;
  stage: IExecutionStage;
  application: Application;
}

export interface IManualJudgmentApprovalState {
  submitting: boolean;
  judgmentDecision: string;
  judgmentInput: { value?: string };
  error: boolean;
}

export class ManualJudgmentApproval extends React.Component<
  IManualJudgmentApprovalProps,
  IManualJudgmentApprovalState
> {
  constructor(props: IManualJudgmentApprovalProps) {
    super(props);
    this.state = {
      submitting: false,
      judgmentDecision: null,
      judgmentInput: {},
      error: false,
    };
  }

  private provideJudgment(judgmentDecision: string): void {
    const { application, execution, stage } = this.props;
    const judgmentInput: string = this.state.judgmentInput ? this.state.judgmentInput.value : null;
    this.setState({ submitting: true, error: false, judgmentDecision });
    ReactInjector.manualJudgmentService.provideJudgment(application, execution, stage, judgmentDecision, judgmentInput);
  }

  private isSubmitting(decision: string): boolean {
    return (
      this.props.stage.context.judgmentStatus === decision ||
      (this.state.submitting && this.state.judgmentDecision === decision)
    );
  }

  private handleJudgementChanged = (option: Option): void => {
    this.setState({ judgmentInput: { value: option.value as string } });
  };

  private handleContinueClick = (): void => {
    this.provideJudgment('continue');
  };

  private handleStopClick = (): void => {
    this.provideJudgment('stop');
  };

  public render(): React.ReactElement<ManualJudgmentApproval> {
    const stage: IExecutionStage = this.props.stage,
      status: string = stage.status;

    const options: Option[] = (stage.context.judgmentInputs || []).map((o: { value: string }) => {
      return { value: o.value, label: o.value };
    });

    const showOptions =
      !['SKIPPED', 'SUCCEEDED'].includes(status) && (!stage.context.judgmentStatus || status === 'RUNNING');

    const hasInstructions = !!stage.context.instructions;
    const { ButtonBusyIndicator } = NgReact;

    return (
      <div>
        {hasInstructions && (
          <div>
            <div>
              <b>Instructions</b>
            </div>
            <Markdown message={stage.context.instructions} />
          </div>
        )}
        {showOptions && (
          <div>
            {options.length > 0 && (
              <div>
                <p>
                  <b>Judgment Input</b>
                </p>
                <Select
                  options={options}
                  clearable={false}
                  value={this.state.judgmentInput.value}
                  onChange={this.handleJudgementChanged}
                />
              </div>
            )}
            <div className="action-buttons">
              <button
                className="btn btn-danger"
                onClick={this.handleStopClick}
                disabled={
                  this.state.submitting ||
                  stage.context.judgmentStatus ||
                  (options.length && !this.state.judgmentInput.value)
                }
              >
                {this.isSubmitting('stop') && <ButtonBusyIndicator />}
                {stage.context.stopButtonLabel || 'Stop'}
              </button>
              <button
                className="btn btn-primary"
                disabled={
                  this.state.submitting ||
                  stage.context.judgmentStatus ||
                  (options.length && !this.state.judgmentInput.value)
                }
                onClick={this.handleContinueClick}
              >
                {this.isSubmitting('continue') && <ButtonBusyIndicator />}
                {stage.context.continueButtonLabel || 'Continue'}
              </button>
            </div>
          </div>
        )}
        {this.state.error && (
          <div className="error-message">There was an error recording your decision. Please try again.</div>
        )}
      </div>
    );
  }
}
