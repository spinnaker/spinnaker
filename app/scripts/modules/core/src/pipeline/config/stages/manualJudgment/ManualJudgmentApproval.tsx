import { IPromise } from 'angular';
import * as React from 'react';
import Select, { Option } from 'react-select';
import 'react-select/dist/react-select.css';
import { BindAll } from 'lodash-decorators';

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

@BindAll()
export class ManualJudgmentApproval extends React.Component<IManualJudgmentApprovalProps, IManualJudgmentApprovalState> {

  constructor(props: IManualJudgmentApprovalProps) {
    super(props);
    this.state = {
      submitting: false,
      judgmentDecision: null,
      judgmentInput: {},
      error: false,
    };
  }

  private provideJudgment(judgmentDecision: string): IPromise<void> {
    const judgmentInput: string = this.state.judgmentInput ? this.state.judgmentInput.value : null;
    this.setState({ submitting: true, error: false, judgmentDecision });
    return ReactInjector.manualJudgmentService.provideJudgment(this.props.execution, this.props.stage, judgmentDecision, judgmentInput)
      .then(() => this.judgmentMade())
      .catch(() => this.judgmentFailure());
  }

  private judgmentMade(): void {
    // do not update the submitting state - the reload of the executions will clear it out; otherwise,
    // there is a flash on the screen when we go from submitting to not submitting to the buttons not being there.
    this.props.application.activeState.refresh(true);
    this.setState({ submitting: false });
  }

  private judgmentFailure(): void {
    this.setState({ submitting: false, error: true });
  }

  private isSubmitting(decision: string): boolean {
    return this.props.stage.context.judgmentStatus === decision ||
      (this.state.submitting && this.state.judgmentDecision === decision);
  }

  private handleJudgementChanged(option: Option): void {
    this.setState({ judgmentInput: { value: option.value as string } });
  }

  private handleContinueClick(): void {
    this.provideJudgment('continue');
  }

  private handleStopClick(): void {
    this.provideJudgment('stop');
  }

  public render(): React.ReactElement<ManualJudgmentApproval> {
    const stage: IExecutionStage = this.props.stage,
          status: string = stage.status;

    const options: Option[] = (stage.context.judgmentInputs || [])
      .map((o: {value: string}) => { return { value: o.value, label: o.value }; });

    const showOptions = !['SKIPPED', 'SUCCEEDED'].includes(status) && (!stage.context.judgmentStatus || status === 'RUNNING');

    const hasInstructions = !!stage.context.instructions;
    const { ButtonBusyIndicator } = NgReact;

    return (
      <div>
        { hasInstructions && (
          <div>
            <div><b>Instructions</b></div>
            <Markdown message={stage.context.instructions}/>
          </div>
        )}
        { showOptions && (
          <div>
            { options.length > 0 && (
              <div>
                <p><b>Judgment Input</b></p>
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
                className="btn btn-primary"
                disabled={this.state.submitting || stage.context.judgmentStatus}
                onClick={this.handleContinueClick}
              >
                { this.isSubmitting('continue') && (
                  <ButtonBusyIndicator/>
                )}
                {stage.context.continueButtonLabel || 'Continue'}
              </button>
              <button
                className="btn btn-danger"
                onClick={this.handleStopClick}
                disabled={this.state.submitting || stage.context.judgmentStatus}
              >
                { this.isSubmitting('stop') && (
                  <ButtonBusyIndicator/>
                )}
                {stage.context.stopButtonLabel || 'Stop'}
              </button>
            </div>
          </div>
        )}
        { this.state.error && (
          <div className="error-message">
            There was an error recording your decision. Please try again.
          </div>
        )}
      </div>
    );
  }
}
