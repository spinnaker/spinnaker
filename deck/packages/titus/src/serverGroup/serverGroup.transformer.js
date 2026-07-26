import _ from 'lodash';

import { AccountService } from '@spinnaker/core';

function normalizeServerGroup(serverGroup) {
  return AccountService.getCredentialsKeyedByAccount('titus').then((credentialsKeyedByAccount) => {
    if (serverGroup.account && credentialsKeyedByAccount[serverGroup.account]) {
      serverGroup.awsAccount = credentialsKeyedByAccount[serverGroup.account].awsAccount;
    }
    return serverGroup;
  });
}

function convertServerGroupCommandToDeployConfiguration(base) {
  // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
  const command = _.defaults({ backingData: [], viewState: [] }, base);
  if (base.viewState.mode !== 'clone') {
    delete command.source;
  }
  command.account = command.credentials;

  if (!command.efs.mountPoint || !command.efs.efsId || !command.efs.mountPerm) {
    delete command.efs;
  }

  delete command.viewState;
  delete command.backingData;
  delete command.selectedProvider;
  return command;
}

/**
 * @param {{ name: string }} serverGroup
 * @returns {import('@spinnaker/amazon').IScalingPolicy}
 */
function constructNewStepScalingPolicyTemplate(serverGroup) {
  return {
    alarms: [
      {
        namespace: 'NFLX/EPIC',
        metricName: '',
        threshold: 50,
        statistic: 'Average',
        comparisonOperator: 'GreaterThanThreshold',
        evaluationPeriods: 1,
        disableEditingDimensions: true,
        dimensions: [{ name: 'AutoScalingGroupName', value: serverGroup.name }],
        period: 60,
      },
    ],
    adjustmentType: 'ChangeInCapacity',
    stepAdjustments: [
      {
        scalingAdjustment: 1,
        metricIntervalLowerBound: 0,
      },
    ],
    cooldown: 300,
  };
}

/**
 * @param {{ name: string }} serverGroup
 * @returns {import('@spinnaker/amazon').ITargetTrackingPolicy}
 */
function constructNewTargetTrackingPolicyTemplate(serverGroup) {
  return {
    alarms: [],
    targetTrackingConfiguration: {
      targetValue: null,
      disableScaleIn: false,
      customizedMetricSpecification: {
        namespace: 'NFLX/EPIC',
        metricName: '',
        statistic: 'Average',
        dimensions: [{ name: 'AutoScalingGroupName', value: serverGroup.name }],
      },
      scaleInCooldown: 300,
      scaleOutCooldown: 300,
    },
  };
}

export class TitusServerGroupTransformerDelegate {
  convertServerGroupCommandToDeployConfiguration = convertServerGroupCommandToDeployConfiguration;
  normalizeServerGroup = normalizeServerGroup;
  constructNewStepScalingPolicyTemplate = constructNewStepScalingPolicyTemplate;
  constructNewTargetTrackingPolicyTemplate = constructNewTargetTrackingPolicyTemplate;
}

export const TitusServerGroupTransformer = new TitusServerGroupTransformerDelegate();
