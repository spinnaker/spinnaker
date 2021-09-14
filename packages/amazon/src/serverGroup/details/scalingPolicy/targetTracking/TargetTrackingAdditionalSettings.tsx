import { set } from 'lodash';
import * as React from 'react';
import { CheckboxInput, HelpField, NumberInput } from '@spinnaker/core';
import { IUpsertScalingPolicyCommand } from '../ScalingPolicyWriter';

export interface ITargetTrackingAdditionalSettingsProps {
  command: IUpsertScalingPolicyCommand;
  cooldowns?: Boolean;
  policyName?: string;
  updateCommand: (command: IUpsertScalingPolicyCommand) => void;
}

export const TargetTrackingAdditionalSettings = ({
  command,
  cooldowns,
  policyName,
  updateCommand,
}: ITargetTrackingAdditionalSettingsProps) => {
  const setCommandField = (path: string, value: number) => {
    const newCommand = { ...command };
    set(newCommand, path, value);
    updateCommand(newCommand);
  };
  const scaleInDisabled = Boolean(command.targetTracking?.disableScaleIn);
  return (
    <div className="section-body section-additional-settings">
      {policyName && (
        <div className="row">
          <div className="col-md-2 sm-label-right">Policy Name</div>
          <div className="col-md-10 content-fields">{policyName}</div>
        </div>
      )}
      {Boolean(command.esitmatedInstanceWarmup) && (
        <div className="row">
          <div className="col-md-2 sm-label-right">Warmup</div>
          <div className="col-md-10 content-fields">
            <span className="form-control-static select-placeholder">Instances need</span>
            <NumberInput
              value={command.estimatedInstanceWarmup}
              onChange={(e) => setCommandField('estimatedInstanceWarmup', Number.parseInt(e.target.value))}
              inputClassName="form-control input-sm sp-margin-xs-xaxis"
            />
            <span className="input-label"> seconds to warm up </span>
          </div>
        </div>
      )}
      <div className="row">
        <div className="col-md-2 sm-label-right">Scale In</div>
        <div className="col-md-9">
          <div className="checkbox">
            <CheckboxInput
              text="Disable Scale-downs"
              checked={scaleInDisabled}
              onChange={(e) => setCommandField('targetTrackingConfiguration.disableScaleIn', e.target.checked)}
            />
            <div className="small">
              <p>
                This option disables scale-downs for the target tracking policy, while keeping the scale-ups. This means
                that ASG will not scale down unless you explicitly set up a separate step policy to scale it down.
              </p>
              <p>This is useful when you have special requirements, such as gradual or delayed scale-down.</p>
            </div>
          </div>
        </div>
      </div>
      <div className="row">
        <div className="col-md-10 col-md-offset-1 well">
          {scaleInDisabled && (
            <div>
              This policy will not scale down. Make sure you have another policy (either TT or Step) that will scale
              down this ASG.
            </div>
          )}
          {!scaleInDisabled && (
            <div>
              This policy will scale both up and down. Make sure you don't have other scaling policies, as they will
              likely interfere with each other.
            </div>
          )}
        </div>
      </div>
      {cooldowns && !scaleInDisabled && (
        <div className="row">
          <div className="col-md-3 sm-label-right">
            <span className="sp-margin-xs-right">Scale In Cooldown</span>
            <HelpField id="titus.autoscaling.scaleIn.cooldown" />
          </div>
          <div className="col-md-9 content-fields">
            <NumberInput
              value={command.targetTrackingConfiguration.scaleInCooldown}
              onChange={(e) =>
                setCommandField('targetTrackingConfiguration.scaleInCooldown', Number.parseInt(e.target.value))
              }
              inputClassName="sp-margin-xs-xaxis"
            />
            <span className="input-label"> seconds </span>
          </div>
        </div>
      )}
      {cooldowns && (
        <div className="row">
          <div className="col-md-3 sm-label-right">
            <span className="sp-margin-xs-right">Scale Out Cooldown</span>
            <HelpField id="titus.autoscaling.scaleOut.cooldown" />
          </div>
          <div className="col-md-9 content-fields">
            <NumberInput
              value={command.targetTrackingConfiguration.scaleInCooldown}
              onChange={(e) =>
                setCommandField('targetTrackingConfiguration.scaleOutCooldown', Number.parseInt(e.target.value))
              }
              inputClassName="sp-margin-xs-xaxis"
            />
            <span className="input-label"> seconds </span>
          </div>
        </div>
      )}
    </div>
  );
};
