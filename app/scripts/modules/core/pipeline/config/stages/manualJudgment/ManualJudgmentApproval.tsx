import {IPromise} from 'angular';
import * as React from 'react';
import * as Select from 'react-select';
import * as DOMPurify from 'dompurify';
import 'react-select/dist/react-select.css';
import autoBindMethods from 'class-autobind-decorator';

import {IExecution, IExecutionStage} from 'core/domain';
import {Application} from 'core/application/application.model';
import {ReactInjector} from 'core/react';
import {ButtonBusyIndicator} from 'core/forms/buttonBusyIndicator/ButtonBusyIndicator';

interface IProps {
  execution: IExecution;
  stage: IExecutionStage;
  application: Application;
}

interface IState {
  submitting: boolean;
  judgmentDecision: string;
  judgmentInput: { value?: string };
  error: boolean;
}

@autoBindMethods
export class ManualJudgmentApproval extends React.Component<IProps, IState> {
  constructor(props: IProps) {
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
    this.setState({submitting: true, error: false, judgmentDecision});
    return ReactInjector.manualJudgmentService.provideJudgment(this.props.execution, this.props.stage, judgmentDecision, judgmentInput)
      .then(() => this.judgmentMade())
      .catch(() => this.judgmentFailure());
  }

  private judgmentMade(): void {
    // do not update the submitting state - the reload of the executions will clear it out; otherwise,
    // there is a flash on the screen when we go from submitting to not submitting to the buttons not being there.
    this.props.application.getDataSource('executions').refresh();
  }

  private judgmentFailure(): void {
    this.setState({submitting: false, error: true});
  }

  private isSubmitting(decision: string): boolean {
    return this.props.stage.context.judgmentStatus === decision ||
      (this.state.submitting && this.state.judgmentDecision === decision);
  }

  private handleJudgementChanged(option: Select.Option): void {
    this.setState({ judgmentInput: { value: option.value as string } });
  }

  private handleContinueClick(): void {
    this.provideJudgment('continue');
  }

  private handleStopClick(): void {
    this.provideJudgment('stop');
  }

  private getInstructions(): any {
    return {
      __html: DOMPurify.sanitize(this.props.stage.context.instructions)
    };
  }

  public render(): React.ReactElement<ManualJudgmentApproval> {
    const stage: IExecutionStage = this.props.stage,
          status: string = stage.status;

    const options: Select.Option[] = (stage.context.judgmentInputs || [])
      .map((o: {value: string}) => { return {value: o.value, label: o.value}; });

    const showOptions = status !== 'SKIPPED' && (!stage.context.judgmentStatus || status === 'RUNNING');

    const hasInstructions = !!stage.context.instructions;

    return (
      <div>
        { hasInstructions && (
          <div>
            <div><b>Instructions</b></div>
            <p dangerouslySetInnerHTML={this.getInstructions()}/>
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
                Continue
              </button>
              <button
                className="btn btn-danger"
                onClick={this.handleStopClick}
                disabled={this.state.submitting || stage.context.judgmentStatus}
              >
                { this.isSubmitting('stop') && (
                  <ButtonBusyIndicator/>
                )}
                Stop
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
