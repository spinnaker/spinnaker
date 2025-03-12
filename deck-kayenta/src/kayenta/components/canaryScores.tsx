import classNames from 'classnames';
import { isString } from 'lodash';
import * as React from 'react';

import { HelpField } from '@spinnaker/core';

import './canaryScores.less';

export interface IScoreConfig {
  successfulScore: string;
  unhealthyScore: string;
}

export interface ICanaryScoresProps {
  onChange: (scoreConfig: IScoreConfig) => void;
  successfulHelpFieldId?: string;
  successfulLabel?: string;
  successfulScore: string;
  unhealthyHelpFieldId?: string;
  unhealthyLabel?: string;
  unhealthyScore: string;
  disabled?: boolean;
}

export interface ICanaryScoresState {
  successfulTouched: boolean;
  unhealthyTouched: boolean;
}

export function CanaryScores(props: ICanaryScoresProps) {
  const [successfulTouched, setSuccessfulTouched] = React.useState(!!props.successfulScore);
  const [unhealthyTouched, setUnhealthyTouched] = React.useState(!!props.unhealthyScore);

  const { successfulScore, unhealthyScore } = props;

  const isExpression = (scoreValue: string): boolean => {
    return isString(scoreValue) && scoreValue.includes('${');
  };

  const hasExpressions = isExpression(unhealthyScore) || isExpression(successfulScore);

  let successful: number;
  let unhealthy: number;
  if (!hasExpressions) {
    successful = Number(successfulScore);
    unhealthy = Number(unhealthyScore);
  }

  const handleSuccessfulChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    props.onChange({
      successfulScore: event.target.value,
      unhealthyScore,
    });
  };

  const handleUnhealthyChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    props.onChange({
      successfulScore,
      unhealthyScore: event.target.value,
    });
  };

  const errors = {
    successful: '',
    unhealthy: '',
  };

  if (successfulTouched) {
    if (successful <= 0) {
      errors.successful = 'Must be positive';
    }
    if (successful >= 100) {
      errors.successful = 'Must be less than 100';
    }
  }

  if (unhealthyTouched) {
    if (unhealthy <= 0) {
      errors.unhealthy = 'Must be positive';
    }
    if (unhealthy >= 100) {
      errors.unhealthy = 'Must be less than 100';
    }
  }

  if (successfulTouched && unhealthyTouched) {
    if (successful <= unhealthy) {
      errors.successful = `Must be greater than ${props.unhealthyLabel}`;
    }
  }

  const invalid = !(successful && unhealthy);
  return (
    <div>
      {hasExpressions && (
        <div className="form-group">
          <div className="col-md-2 col-md-offset-1 sm-label-right">Canary Scores</div>
          <div className="col-md-9 form-control-static">Expressions are currently being used for canary scores.</div>
        </div>
      )}
      {!hasExpressions && (
        <div className="canary-score">
          <div className="form-group">
            <div className="col-md-2 col-md-offset-1 sm-label-right">
              {props.unhealthyLabel || 'Unhealthy Score '}
              <HelpField id={props.unhealthyHelpFieldId || 'pipeline.config.canary.unhealthyScore'} />
            </div>
            <div className="col-md-2">
              <input
                type="number"
                required={true}
                disabled={props.disabled}
                onBlur={() => setUnhealthyTouched(true)}
                value={Number.isNaN(unhealthy) ? '' : unhealthy}
                onChange={handleUnhealthyChange}
                className={classNames('form-control', 'input-sm', {
                  'ng-invalid': !!errors.unhealthy,
                  'ng-invalid-validate-min': !!errors.unhealthy,
                })}
              />
              {errors.unhealthy && <div className="error-message">{errors.unhealthy}</div>}
            </div>
            <div className="col-md-2 col-md-offset-1 sm-label-right">
              {props.successfulLabel || 'Successful Score '}
              <HelpField id={props.successfulHelpFieldId || 'pipeline.config.canary.successfulScore'} />
            </div>
            <div className="col-md-2">
              <input
                type="number"
                required={true}
                disabled={props.disabled}
                onBlur={() => setSuccessfulTouched(true)}
                value={Number.isNaN(successful) ? '' : successful}
                onChange={handleSuccessfulChange}
                className={classNames('form-control', 'input-sm', {
                  'ng-invalid': !!errors.successful,
                  'ng-invalid-validate-max': !!errors.successful,
                })}
              />
              {errors.successful && <div className="error-message">{errors.successful}</div>}
            </div>
          </div>
          <div className="row">
            <div className="col-md-offset-1 col-md-10">
              <div className="progress">
                <div className="progress-bar progress-bar-danger" style={{ width: `${invalid ? 0 : unhealthy}%` }} />
                <div
                  className="progress-bar progress-bar-warning"
                  style={{ width: `${invalid ? 0 : 100 - (unhealthy + (100 - successful))}%` }}
                />
                <div
                  className="progress-bar progress-bar-success"
                  style={{ width: `${invalid ? 0 : 100 - successful}%` }}
                />
                <div className="progress-bar progress-bar-warning" style={{ width: `${invalid ? 100 : 0}%` }} />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
