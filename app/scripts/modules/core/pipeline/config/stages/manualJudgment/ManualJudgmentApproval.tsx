import {IPromise} from 'angular';
import * as React from 'react';
import * as Select from 'react-select';

import {IExecution} from 'core/domain/IExecution';
import {IExecutionStage} from 'core/domain/IExecutionStage';
import {Application} from 'core/application/application.model';
import {manualJudgmentService} from './manualJudgment.service';
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
    return manualJudgmentService.provideJudgment(this.props.execution, this.props.stage, judgmentDecision, judgmentInput)
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

  private handleJudgementChanged = (option: Select.Option) => this.setState({judgmentInput: {value: option.value as string}});

  private handleContinueClick = () => this.provideJudgment('continue');

  private handleStopClick = () => this.provideJudgment('stop');

  public render(): React.ReactElement<ManualJudgmentApproval> {
    const stage: IExecutionStage = this.props.stage,
          status: string = stage.status;

    const options: Select.Option[] = (stage.context.judgmentInputs || [])
      .map((o: {value: string}) => { return {value: o.value, label: o.value}; });

    const buttonMargin: any = { margin: '0 15px' };

    const showOptions = status !== 'SKIPPED' && (!stage.context.judgmentStatus || status === 'RUNNING');

    return (
      <div>
        { showOptions && (
          <div>
            { options.length > 0 && (
              <div className="form-group col-md-12">
                <p><b>Judgment Input</b></p>
                <Select options={options}
                        clearable={false}
                        value={this.state.judgmentInput.value}
                        onChange={this.handleJudgementChanged}/>
              </div>
            )}
            <button className="btn btn-primary"
                    style={buttonMargin}
                    disabled={this.state.submitting || stage.context.judgmentStatus}
                    onClick={this.handleContinueClick}>
              { this.isSubmitting('continue') && (
                <ButtonBusyIndicator/>
              )}
              Continue
            </button>
            <button className="btn btn-danger"
                    onClick={this.handleStopClick}
                    disabled={this.state.submitting || stage.context.judgmentStatus}>
              { this.isSubmitting('stop') && (
                <ButtonBusyIndicator/>
              )}
              Stop
            </button>
          </div>
        )}
        { this.state.error && (
          <div className="col-md-12 error-message">
            There was an error recording your decision. Please try again.
          </div>
        )}
      </div>
    );
  }
}
