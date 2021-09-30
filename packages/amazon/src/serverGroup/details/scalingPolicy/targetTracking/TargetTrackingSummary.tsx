import * as React from 'react';

import type { Application, IServerGroup } from '@spinnaker/core';
import {
  CloudProviderRegistry,
  ConfirmationModalService,
  HoverablePopover,
  ReactModal,
  robotToHuman,
  TaskExecutor,
} from '@spinnaker/core';

import type { ITargetTrackingPolicy } from '../../../../domain';
import './TargetTrackingSummary.less';

export interface ITargetTrackingPolicySummaryProps {
  application: Application;
  policy: ITargetTrackingPolicy;
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

export const TargetTrackingSummary = ({ application, policy, serverGroup }: ITargetTrackingPolicySummaryProps) => {
  const provider = serverGroup.type || serverGroup.cloudProvider || 'aws';
  const providerConfig = CloudProviderRegistry.getValue(provider, 'serverGroup');
  const policyTitle = provider === 'aws' ? policy.policyName : policy.id;

  const UpsertModalComponent = providerConfig.UpsertTargetTrackingModal;
  const PopoverContent = providerConfig.TargetTrackingChart;

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

  const config = policy.targetTrackingConfiguration;
  return (
    <div className="TargetTrackingSummary">
      <HoverablePopover
        Component={() => <PopoverContent config={policy.targetTrackingConfiguration} serverGroup={serverGroup} />}
        placement="left"
        title={policyTitle}
      >
        <div>
          <div className="label label-default">{robotToHuman(policy.policyType).toUpperCase()}</div>
          <div>
            <b>Target</b>
            <span className="sp-margin-xs-left">
              {config.predefinedMetricSpecification?.predefinedMetricType ||
                config.customizedMetricSpecification?.metricName}
            </span>
            {Boolean(config.customizedMetricSpecification?.statistic) && <span>{` @ ${config.targetValue}`}</span>}
          </div>
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
  );
};
