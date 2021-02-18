import { set } from 'lodash';
import React from 'react';

import { HelpField, IDeploymentStrategyAdditionalFieldsProps, IServerGroupCommand } from '@spinnaker/core';

export interface IRollingRedBlackCommand extends IServerGroupCommand {
  targetPercentages: number[];
  delayBeforeDisableSec: number;
  delayBeforeScaleDownSec: number;
  maxRemainingAsgs: number;
  rollback: {
    onFailure: boolean;
  };
  scaleDown: boolean;
}

export interface IRollingRedBlackStrategyAdditionalFieldsProps extends IDeploymentStrategyAdditionalFieldsProps {
  command: IRollingRedBlackCommand;
}

export class AdditionalFields extends React.Component<IRollingRedBlackStrategyAdditionalFieldsProps> {
  private handlePercentChange = (key: string, value: any, index: number) => {
    const percentages = this.props.command.targetPercentages;
    percentages[index] = value;
    set(this.props.command, key, percentages);
    this.forceUpdate();
  };

  private onDelete = (index: number) => {
    const percentages = this.props.command.targetPercentages;
    percentages.splice(index, 1);
    set(this.props.command, 'targetPercentages', percentages);
    this.forceUpdate();
  };

  private addPercent = () => {
    const percentages = this.props.command.targetPercentages;
    percentages.push(100);
    set(this.props.command, 'targetPercentages', percentages);
    this.forceUpdate();
  };

  private handleChange = (key: string, value: any) => {
    set(this.props.command, key, value);
    this.forceUpdate();
  };

  public render() {
    const { command } = this.props;
    return (
      <div className="form-group">
        <div className="col-md-12">
          <div className="col-md-12 checkbox">
            <label>
              <input
                type="checkbox"
                checked={command.rollback.onFailure}
                onChange={(e) => this.handleChange('rollback.onFailure', e.target.checked)}
              />
              Rollback to previous server group if deployment fails <HelpField id="strategy.rollingRedBlack.rollback" />
            </label>
          </div>
          <div className="col-md-12 form-inline sp-margin-m-bottom sp-margin-m-top">
            <label>
              Wait Before Scale Down &nbsp;
              <HelpField content="Time to wait before scaling down source server group" />
            </label>
            <div>
              <input
                className="form-control input-sm"
                style={{ width: '60px' }}
                min="0"
                type="number"
                value={command.delayBeforeDisableSec}
                onChange={(e) => this.handleChange('delayBeforeDisableSec', e.target.value)}
                placeholder="0"
              />
              &nbsp; seconds
            </div>
          </div>
          <div className="col-md-12 form-inline sp-margin-m-bottom">
            <label>
              Maximum number of server groups to leave &nbsp;
              <HelpField id="strategy.redblack.maxRemainingAsgs" />
            </label>
            <div>
              <input
                className="form-control input-sm"
                style={{ width: '60px' }}
                min="2"
                type="number"
                value={command.maxRemainingAsgs}
                onChange={(e) => this.handleChange('maxRemainingAsgs', e.target.value)}
              />
              &nbsp; server groups
            </div>
          </div>
          <div className="col-md-12 form-inline sp-margin-m-bottom">
            <label>
              Wait Before Cluster Scale Down &nbsp;
              <HelpField content="Time to wait before scaling down the cluster after deployment" />
            </label>
            <div>
              <input
                className="form-control input-sm"
                style={{ width: '60px' }}
                min="0"
                type="number"
                value={command.delayBeforeScaleDownSec}
                onChange={(e) => this.handleChange('delayBeforeScaleDownSec', e.target.value)}
                placeholder="0"
              />
              &nbsp; seconds
            </div>
          </div>
          <label>
            Scale by percentages &nbsp;
            <HelpField content="Steps to get to full capacity in percent" />
          </label>
          <table className="table table-condensed packed metadata">
            <tbody>
              {command.targetPercentages.map((percentage: number, index: number) => {
                return (
                  <tr key={index}>
                    <td>
                      <div className="sp-margin-m-bottom">
                        <input
                          className="form-control"
                          min="0"
                          type="number"
                          value={percentage}
                          onChange={(e) => this.handlePercentChange('targetPercentages', e.target.value, index)}
                          placeholder="100"
                        />
                      </div>
                    </td>
                    <td>
                      <a className="btn btn-link sm-label" onClick={() => this.onDelete(index)}>
                        <span className="glyphicon glyphicon-trash" />
                      </a>
                    </td>
                  </tr>
                );
              })}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={2}>
                  <button type="button" className="add-new col-md-12" onClick={() => this.addPercent()}>
                    <span className="glyphicon glyphicon-plus-sign" /> Add percentage
                  </button>
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    );
  }
}
