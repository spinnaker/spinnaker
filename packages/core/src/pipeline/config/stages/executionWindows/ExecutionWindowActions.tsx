import { get } from 'lodash';
import React from 'react';

import { Application } from '../../../../application/application.model';
import { ConfirmationModalService } from '../../../../confirmationModal';
import { DAYS_OF_WEEK } from './daysOfWeek';
import { IExecution, IExecutionStage } from '../../../../domain';
import { ReactInjector } from '../../../../reactShims';
import { SystemTimezone } from '../../../../utils/SystemTimezone';
import { timePickerTime } from '../../../../utils/timeFormatters';

export interface IExecutionWindowActionsProps {
  execution: IExecution;
  stage: IExecutionStage;
  application: Application;
}

export interface IExecutionWindowActionsState {
  dayText: string;
}

export interface IExecutionWindowAllowlistEntry {
  startHour: number;
  startMin: number;
  endHour: number;
  endMin: number;
}

export const DEFAULT_SKIP_WINDOW_TEXT =
  'The pipeline will proceed immediately, continuing to the next step in the stage.';

export class ExecutionWindowActions extends React.Component<
  IExecutionWindowActionsProps,
  IExecutionWindowActionsState
> {
  constructor(props: IExecutionWindowActionsProps) {
    super(props);
    const days = props.stage.context.restrictedExecutionWindow?.days;
    let dayText = 'Everyday';
    if (days && days.length > 0) {
      dayText = this.replaceDays(days).join(', ');
    }
    this.state = {
      dayText,
    };
  }

  private finishWaiting = (e: React.MouseEvent<HTMLElement>): void => {
    const { executionService } = ReactInjector;
    (e.target as HTMLElement).blur(); // forces closing of the popover when the modal opens
    const { application, execution, stage } = this.props;

    const matcher = (updated: IExecution) => {
      const match = updated.stages.find((test) => test.id === stage.id);
      return match.status !== 'RUNNING';
    };

    const data = { skipRemainingWait: true };
    ConfirmationModalService.confirm({
      header: 'Really skip execution window?',
      buttonText: 'Skip',
      body: stage.context.skipWindowText || `<p>${DEFAULT_SKIP_WINDOW_TEXT}</p>`,
      submitMethod: () => {
        return executionService
          .patchExecution(execution.id, stage.id, data)
          .then(() => executionService.waitUntilExecutionMatches(execution.id, matcher))
          .then((updated) => executionService.updateExecution(application, updated));
      },
    });
  };

  private replaceDays(days: number[]): string[] {
    const daySet = new Set(days);
    return DAYS_OF_WEEK.filter((day) => daySet.has(day.ordinal)).map((day) => day.label);
  }

  public render() {
    const { stage } = this.props;
    return (
      <div>
        <h5>Execution Windows Configuration</h5>
        <strong>Stage execution can only run:</strong>
        <dl className="dl-narrow dl-horizontal">
          {get(stage, 'context.restrictedExecutionWindow.whitelist', []).map(
            (entry: IExecutionWindowAllowlistEntry, index: number) => {
              return (
                <div key={index}>
                  <dt>From</dt>
                  <dd>
                    {timePickerTime({ hours: entry.startHour, minutes: entry.startMin })}
                    <strong style={{ display: 'inline-block', margin: '0 5px' }}> to </strong>
                    {timePickerTime({ hours: entry.endHour, minutes: entry.endMin })}
                    <strong>
                      {' '}
                      <SystemTimezone />{' '}
                    </strong>
                  </dd>
                </div>
              );
            },
          )}
          <dt>On</dt>
          <dd>{this.state.dayText}</dd>
        </dl>
        {stage.context.skipRemainingWait && (
          <div>
            <span>(skipped </span>
            {stage.context.lastModifiedBy && <span> by {stage.context.lastModifiedBy}</span>})
          </div>
        )}
        {stage.isSuspended && (
          <div className="action-buttons">
            <button className="btn btn-xs btn-primary" onClick={this.finishWaiting}>
              <span style={{ marginRight: '5px' }} className="small glyphicon glyphicon-fast-forward" />
              Skip remaining window
            </button>
          </div>
        )}
      </div>
    );
  }
}
