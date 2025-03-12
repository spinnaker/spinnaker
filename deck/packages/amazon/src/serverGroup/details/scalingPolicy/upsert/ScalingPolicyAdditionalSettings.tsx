import { set } from 'lodash';
import * as React from 'react';

import { HelpField, NumberInput } from '@spinnaker/core';

import type { IUpsertScalingPolicyCommand } from '../ScalingPolicyWriter';
import './ScalingPolicyAdditionalSettings.less';

export interface IScalingPolicyAdditionalSettingsProps {
  command: IUpsertScalingPolicyCommand;
  isInstanceType: boolean;
  isNew: boolean;
  operator: string;
  updateCommand: (command: IUpsertScalingPolicyCommand) => void;
}

export const ScalingPolicyAdditionalSettings = ({
  command,
  isInstanceType,
  isNew,
  operator,
  updateCommand,
}: IScalingPolicyAdditionalSettingsProps) => {
  const setCommandField = (path: string, value: number) => {
    const newCommand = { ...command };
    set(newCommand, path, value);
    updateCommand(newCommand);
  };

  return (
    <div className="ScalingPolicyAdditionalSettings">
      <h4 className="section-heading">Additional Settings</h4>
      <div className="section-body section-additional-settings">
        {!isNew && (
          <div className="row">
            <div className="col-md-2 sm-label-right">Policy Name</div>
            <div className="col-md-10 horizontal middle">
              <span className="form-control-static select-placeholder">{command.name}</span>
            </div>
          </div>
        )}
        {!isInstanceType && (
          <div className="row">
            <div className="col-md-2 sm-label-right">Adjustment Step</div>
            <div className="col-md-10 horizontal middle">
              <span className="form-control-static select-placeholder">
                {`${operator} instances in increments of at least `}
              </span>
              <NumberInput
                value={command.minAdjustmentMagnitude}
                onChange={(e) => setCommandField('minAdjustmentMagnitude', Number.parseInt(e.target.value))}
                inputClassName="sp-margin-xs-xaxis input-sm number-input-sm"
              />
              <span className="input-label"> instance(s) </span>
            </div>
          </div>
        )}
        {Boolean(command.simple) && (
          <div className="row">
            <div className="col-md-2 sm-label-right">Cooldown</div>
            <div className="col-md-10 horizontal middle">
              <span className="form-control-static select-placeholder"> Wait at least </span>
              <NumberInput
                value={command.simple?.cooldown}
                onChange={(e) => setCommandField('simple.cooldown', Number.parseInt(e.target.value))}
                inputClassName="sp-margin-xs-xaxis input-sm number-input-sm"
              />
              <span className="input-label"> seconds before another scaling event </span>
            </div>
          </div>
        )}
        {Boolean(command.step?.estimatedInstanceWarmup) && operator !== 'Remove' && (
          <div className="row">
            <div className="col-md-2 sm-label-right">Warmup</div>
            <div className="col-md-10 horizontal middle">
              <span className="form-control-static select-placeholder">Instances need</span>
              <NumberInput
                value={command.step.estimatedInstanceWarmup}
                onChange={(e) => setCommandField('step.estimatedInstanceWarmup', Number.parseInt(e.target.value))}
                inputClassName="sp-margin-xs-xaxis input-sm number-input-sm"
              />
              <span className="input-label"> seconds to warm up after each step </span>
            </div>
          </div>
        )}
        {Boolean(command.step?.cooldown) && operator !== 'Remove' && (
          <div className="row">
            <div className="col-md-2 sm-label-right">
              <span className="sp-margin-xs-right">Cooldown</span>
              <HelpField id={`${command.cloudProvider || command.provider}.autoscaling.cooldown`} />
            </div>
            <div className="col-md-10 horizontal middle">
              <NumberInput
                value={command.step?.cooldown}
                onChange={(e) => setCommandField('step.cooldown', Number.parseInt(e.target.value))}
                inputClassName="sp-margin-xs-xaxis input-sm number-input-sm"
              />
              <span className="input-label"> seconds </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
