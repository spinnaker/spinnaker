import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { ICronTriggerConfigProps } from './cronConfig';
import { CronValidatorService } from './cronValidator.service';
import { HelpField } from '../../../../help';

export interface ICronAdvanceState {
  description?: string;
  errorMessage?: string;
}

export class CronAdvance extends React.Component<ICronTriggerConfigProps, ICronAdvanceState> {
  private destroy$ = new Subject();

  constructor(props: ICronTriggerConfigProps) {
    super(props);
    this.state = {
      description: '',
      errorMessage: '',
    };
  }

  public componentDidMount(): void {
    this.validateCronExpression(this.props.trigger.cronExpression);
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private validateCronExpression = (cronExpression: string) => {
    observableFrom(CronValidatorService.validate(cronExpression))
      .pipe(takeUntil(this.destroy$))
      .subscribe(
        (result: { valid: boolean; message?: string; description?: string }) => {
          if (result.valid) {
            this.setState({
              description: result.description
                ? result.description.charAt(0).toUpperCase() + result.description.slice(1)
                : '',
              errorMessage: '',
            });
          } else {
            this.setState({
              description: '',
              errorMessage: result && result.message ? result.message : 'Error validating CRON expression',
            });
          }
        },
        (result: { valid: boolean; message?: string; description?: string }) => {
          this.setState({
            description: '',
            errorMessage: result && result.message ? result.message : 'Error validating CRON expression',
          });
        },
      );
  };

  private onUpdateTrigger = (event: React.ChangeEvent<HTMLInputElement>) => {
    const cronExpression = event.target.value.replace(/\s\s+/g, ' ');
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression,
    });
    this.validateCronExpression(cronExpression);
  };

  public render() {
    const { cronExpression } = this.props.trigger;
    const { description, errorMessage } = this.state;
    return (
      <div>
        <div className="row">
          <div className="col-md-12">
            <strong>Expression</strong>
            <HelpField id="pipeline.config.cron.expression" />{' '}
            <input
              type="text"
              className="form-control input-sm"
              onChange={this.onUpdateTrigger}
              required={true}
              value={cronExpression}
            />
          </div>
        </div>
        <div className="row">
          <div className="col-md-12">
            <p>
              More details about how to create these expressions can be found{' '}
              <a
                href="http://www.quartz-scheduler.org/documentation/2.3.1-SNAPSHOT/tutorials/tutorial-lesson-06.html"
                target="_blank"
              >
                here
              </a>{' '}
              and{' '}
              <a href="https://www.freeformatter.com/cron-expression-generator-quartz.html" target="_blank">
                here
              </a>
              .
            </p>
          </div>
        </div>
        {description && !errorMessage && (
          <div className="row">
            <div className="col-md-12">
              <p>
                <strong>{description}</strong>
              </p>
            </div>
          </div>
        )}
        {errorMessage && (
          <div className="row slide-in">
            <div className="col-md-12 error-message">
              <p>This trigger will NEVER fire.</p>
              {errorMessage}
            </div>
          </div>
        )}
      </div>
    );
  }
}
