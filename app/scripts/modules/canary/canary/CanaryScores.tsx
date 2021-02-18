import { isString } from 'lodash';
import React from 'react';

import { HelpField } from '@spinnaker/core';

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
}

export class CanaryScores extends React.Component<ICanaryScoresProps> {
  public render() {
    const hasExpressions =
      this.isExpression(this.props.unhealthyScore) || this.isExpression(this.props.successfulScore);

    let successful: number, unhealthy: number;
    if (!hasExpressions) {
      successful = parseInt(this.props.successfulScore, 10);
      unhealthy = parseInt(this.props.unhealthyScore, 10);
    }

    const invalid = !(successful && unhealthy);

    return (
      <div>
        {hasExpressions && (
          <div className="form-group">
            <div className="col-md-2 col-md-offset-1 sm-label-right">
              <label>Canary Scores</label>
            </div>
            <div className="col-md-9 form-control-static">Expressions are currently being used for canary scores.</div>
          </div>
        )}
        {!hasExpressions && (
          <div className="canary-score">
            <div className="form-group">
              <div className="col-md-2 col-md-offset-1 sm-label-right">
                <label>{this.props.unhealthyLabel || 'Unhealthy Score'}</label>
                <HelpField id={this.props.unhealthyHelpFieldId || 'pipeline.config.canary.unhealthyScore'} />
              </div>
              <div className="col-md-2">
                <input
                  type="number"
                  required={true}
                  value={Number.isNaN(unhealthy) ? '' : unhealthy}
                  onChange={this.handleUnhealthyChange}
                  className={`form-control input-sm ${
                    this.isUnhealthyScoreValid(successful, unhealthy) ? '' : 'ng-invalid ng-invalid-validate-min'
                  }`}
                />
              </div>
              <div className="col-md-2 col-md-offset-1 sm-label-right">
                <label>{this.props.successfulLabel || 'Successful Score'}</label>
                <HelpField id={this.props.successfulHelpFieldId || 'pipeline.config.canary.successfulScore'} />
              </div>
              <div className="col-md-2">
                <input
                  type="number"
                  required={true}
                  value={Number.isNaN(successful) ? '' : successful}
                  onChange={this.handleSuccessfulChange}
                  className={`form-control input-sm ${
                    this.isSuccessfulScoreValid(successful, unhealthy) ? '' : 'ng-invalid ng-invalid-validate-max'
                  }`}
                />
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

  private handleSuccessfulChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.onChange({
      successfulScore: event.target.value,
      unhealthyScore: this.props.unhealthyScore,
    });
  };

  private handleUnhealthyChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.onChange({
      successfulScore: this.props.successfulScore,
      unhealthyScore: event.target.value,
    });
  };

  private isSuccessfulScoreValid(successfulScore: number, unhealthyScore: number): boolean {
    return successfulScore && (!unhealthyScore || successfulScore > unhealthyScore) && successfulScore <= 100;
  }

  private isUnhealthyScoreValid(successfulScore: number, unhealthyScore: number): boolean {
    return unhealthyScore && (!successfulScore || unhealthyScore < successfulScore) && unhealthyScore >= 0;
  }

  private isExpression(scoreValue: string): boolean {
    return isString(scoreValue) && scoreValue.includes('${');
  }
}
