import * as React from 'react';

import type {
  IAmazonServerGroup,
  ITargetTrackingPolicy,
  ITargetTrackingPolicyCommand,
  IUpsertScalingPolicyCommand,
} from '@spinnaker/amazon';
import { TargetMetricFields, TargetTrackingAdditionalSettings } from '@spinnaker/amazon';
import type { Application, IModalComponentProps } from '@spinnaker/core';
import { TaskMonitorModal } from '@spinnaker/core';

import type { ITitusServerGroup } from '../../../../domain';
import { TitusScalingPolicyCommandBuilder } from '../upsert/TitusScalingPolicyCommandBuilderService';

export interface IUpsertTargetTrackingModalProps extends IModalComponentProps {
  app: Application;
  policy: ITargetTrackingPolicy;
  serverGroup: ITitusServerGroup;
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
    const baseCommand = TitusScalingPolicyCommandBuilder.buildNewCommand('TargetTracking', serverGroup, policy);
    setCommand(baseCommand);
  }, []);

  const mode = policy.id ? 'Update' : 'Create';

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
            allowDualMode={false}
            cloudwatch={true}
            command={command as ITargetTrackingPolicyCommand}
            isCustomMetric={isCustom}
            serverGroup={(serverGroup as unknown) as IAmazonServerGroup}
            toggleMetricType={(t) => setIsCustom(t === 'custom')}
            updateCommand={setCommand}
          />
          <h4 className="section-heading">Additional Settings</h4>
          <TargetTrackingAdditionalSettings
            command={command}
            cooldowns={true}
            policyName={policy.policyName}
            updateCommand={setCommand}
          />
        </div>
      )}
    />
  );
};
