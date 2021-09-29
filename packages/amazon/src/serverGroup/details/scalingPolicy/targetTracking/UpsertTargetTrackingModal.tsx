import * as React from 'react';

import type { Application, IModalComponentProps } from '@spinnaker/core';
import { TaskMonitorModal } from '@spinnaker/core';

import type { ITargetTrackingPolicyCommand, IUpsertScalingPolicyCommand } from '../ScalingPolicyWriter';
import { TargetMetricFields } from './TargetMetricFields';
import { TargetTrackingAdditionalSettings } from './TargetTrackingAdditionalSettings';
import type { IAmazonServerGroup, ITargetTrackingPolicy } from '../../../../domain';
import { ScalingPolicyCommandBuilder } from '../upsert/ScalingPolicyCommandBuilderService';

export interface IUpsertTargetTrackingModalProps extends IModalComponentProps {
  app: Application;
  policy: ITargetTrackingPolicy;
  serverGroup: IAmazonServerGroup;
}

export const UpsertTargetTrackingModal = ({
  app,
  closeModal,
  dismissModal,
  policy,
  serverGroup,
}: IUpsertTargetTrackingModalProps) => {
  const [isCustom, setIsCustom] = React.useState<boolean>(
    Boolean(policy.targetTrackingConfiguration?.customizedMetricSpecification),
  );

  const [command, setCommand] = React.useState<IUpsertScalingPolicyCommand>({} as IUpsertScalingPolicyCommand);
  React.useEffect(() => {
    const baseCommand = ScalingPolicyCommandBuilder.buildNewCommand('TargetTracking', serverGroup, policy);
    setCommand(baseCommand);
  }, []);

  const mode = policy.policyName ? 'Update' : 'Create';
  return (
    <TaskMonitorModal<IUpsertScalingPolicyCommand>
      closeModal={closeModal}
      dismissModal={dismissModal}
      title={`${mode} scaling policy`}
      application={app}
      description={`${mode} scaling policy for ${serverGroup.name}`}
      initialValues={command}
      mapValuesToTask={() => ({
        application: app,
        job: [
          {
            type: 'upsertScalingPolicy',
            ...command,
          },
        ],
      })}
      render={() => (
        <div className="modal-body">
          <h4 className="section-heading">Target Metric</h4>
          <TargetMetricFields
            allowDualMode={true}
            cloudwatch={false}
            command={command as ITargetTrackingPolicyCommand}
            isCustomMetric={isCustom}
            serverGroup={serverGroup}
            toggleMetricType={(t) => setIsCustom(t === 'custom')}
            updateCommand={setCommand}
          />
          <h4 className="section-heading">Additional Settings</h4>
          <TargetTrackingAdditionalSettings
            command={command}
            cooldowns={false}
            policyName={policy.policyName}
            updateCommand={setCommand}
          />
        </div>
      )}
    />
  );
};
