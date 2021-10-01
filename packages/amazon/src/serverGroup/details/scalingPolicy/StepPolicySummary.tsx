import * as React from 'react';

import {
  CloudProviderRegistry,
  ConfirmationModalService,
  HoverablePopover,
  ReactModal,
  robotToHuman,
  TaskExecutor,
} from '@spinnaker/core';
import type { Application, IServerGroup } from '@spinnaker/core';

import type { IAmazonServerGroup, IScalingPolicyView } from '../../../domain';
import { AlarmSummary } from './popover/AlarmSummary';
import { StepPolicyPopoverContent } from './popover/StepPolicyPopoverContent';

import './StepPolicySummary.less';

export interface IStepPolicySummaryProps {
  application: Application;
  policy: IScalingPolicyView;
  serverGroup: IServerGroup;
}

interface IDeletePolicyJob {
  type: string;
  cloudProvider: string;
  credentials: string;
  region: string;
  policyName?: string;
  scalingPolicyID?: string;
  serverGroupName: string;
}

export const StepPolicySummary = ({ application, policy, serverGroup }: IStepPolicySummaryProps) => {
  const provider = serverGroup.type || serverGroup.cloudProvider || 'aws';
  const providerConfig = CloudProviderRegistry.getValue(provider, 'serverGroup');
  const policyTitle = provider === 'aws' ? policy.policyName : policy.id;

  const UpsertModalComponent = providerConfig.UpsertStepPolicyModal;

  const editPolicy = () => {
    const upsertProps = {
      app: application,
      policy,
      serverGroup,
    };

    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    ReactModal.show<typeof UpsertModalComponent>(UpsertModalComponent, upsertProps, modalProps);
  };
  const deletePolicy = () => {
    const taskMonitor = {
      application,
      title: `Deleting scaling policy ${policyTitle}`,
    };

    const jobToSubmit: IDeletePolicyJob = {
      type: 'deleteScalingPolicy',
      cloudProvider: provider,
      credentials: serverGroup.account,
      region: serverGroup.region,
      scalingPolicyID: policy.id,
      serverGroupName: serverGroup.name,
    };

    if (provider === 'aws') {
      delete jobToSubmit.scalingPolicyID;
      jobToSubmit.policyName = policy.policyName;
    }

    ConfirmationModalService.confirm({
      header: `Really delete ${policyTitle}?`,
      buttonText: 'Delete scaling policy',
      account: serverGroup.account,
      taskMonitorConfig: taskMonitor,
      submitMethod: () =>
        TaskExecutor.executeTask({
          application,
          description: `Delete scaling policy ${policyTitle}`,
          job: [jobToSubmit],
        }),
    });
  };

  return (
    <div className="StepPolicySummary">
      <div>
        {!Boolean(policy.alarms?.length) && <div>No alarms configured for this policy - it's safe to delete.</div>}
        {Boolean(policy.alarms?.length) &&
          policy.alarms.map((a) => (
            <div key={`step-summary-${policy.policyName}`}>
              <HoverablePopover
                Component={() => (
                  <StepPolicyPopoverContent policy={policy} serverGroup={serverGroup as IAmazonServerGroup} />
                )}
                placement="left"
                title={policy.policyName}
              >
                <div>
                  <div className="label label-default">{robotToHuman(policy.policyType).toUpperCase()}</div>
                  <AlarmSummary alarm={a} />
                </div>
              </HoverablePopover>
              <div className="actions">
                <button className="btn btn-xs btn-link" onClick={editPolicy}>
                  <span className="glyphicon glyphicon-cog"></span>
                  <span className="sr-only">Edit policy</span>
                </button>
                <button className="btn btn-xs btn-link" onClick={deletePolicy}>
                  <span className="glyphicon glyphicon-trash"></span>
                  <span className="sr-only">Delete policy</span>
                </button>
              </div>
            </div>
          ))}
      </div>
    </div>
  );
};
