import { cloneDeep, find, get } from 'lodash';
import { Observable } from 'rxjs';

import type { IServerGroupDetailsProps } from '@spinnaker/core';
import { AccountService, ClusterTargetBuilder, ServerGroupReader } from '@spinnaker/core';

function transformAwsScalingPolicy(policy: any): any {
  const view = { ...policy };
  view.alarms = policy.alarms || [];
  view.alarms.forEach((alarm: any) => {
    const comparators: { [key: string]: string } = {
      LessThanThreshold: '&lt;',
      GreaterThanThreshold: '&gt;',
      LessThanOrEqualToThreshold: '&le;',
      GreaterThanOrEqualToThreshold: '&ge;',
    };
    alarm.comparator = comparators[alarm.comparisonOperator];
  });
  view.operator = view.scalingAdjustment < 0 ? 'decrease' : 'increase';
  view.absAdjustment = Math.abs(view.scalingAdjustment);
  if (view.stepAdjustments && view.stepAdjustments.length) {
    view.stepAdjustments.forEach((adjustment: any) => {
      adjustment.operator = adjustment.scalingAdjustment < 0 ? 'decrease' : 'increase';
      adjustment.absAdjustment = Math.abs(adjustment.scalingAdjustment);
    });
    const sorter = policy.stepAdjustments.every((adjustment: any) => adjustment.metricIntervalUpperBound !== undefined)
      ? (a: any, b: any) => b.metricIntervalUpperBound - a.metricIntervalUpperBound
      : (a: any, b: any) => a.metricIntervalLowerBound - b.metricIntervalLowerBound;
    view.stepAdjustments.sort(sorter);
  }
  return view;
}

function transformScalingPolicies(serverGroup: any): void {
  serverGroup.scalingPolicies = (serverGroup.scalingPolicies || [])
    .map((p: any) => {
      const { policy } = p;
      const { stepPolicyDescriptor, targetPolicyDescriptor } = policy;
      const policyType = stepPolicyDescriptor ? 'StepScaling' : 'TargetTrackingScaling';
      if (stepPolicyDescriptor) {
        const alarm = stepPolicyDescriptor.alarmConfig;
        alarm.period = alarm.periodSec;
        alarm.namespace = alarm.metricNamespace;
        alarm.disableEditingDimensions = true;
        if (alarm.metricNamespace === 'NFLX/EPIC' && !alarm.dimensions) {
          alarm.dimensions = [{ name: 'AutoScalingGroupName', value: serverGroup.name }];
        }
        alarm.dimensions = alarm.dimensions || [];
        const transformedPolicy = cloneDeep(stepPolicyDescriptor.scalingPolicy);
        transformedPolicy.cooldown = transformedPolicy.cooldownSec;
        transformedPolicy.policyType = policyType;
        transformedPolicy.alarms = [alarm];
        transformedPolicy.id = p.id;
        if (transformedPolicy.stepAdjustments) {
          transformedPolicy.stepAdjustments.forEach((step: any) => {
            step.metricIntervalUpperBound = get(step, 'metricIntervalUpperBound', step.MetricIntervalUpperBound);
            step.metricIntervalLowerBound = get(step, 'metricIntervalLowerBound', step.MetricIntervalLowerBound);
          });
        }
        return transformedPolicy;
      }

      const { customizedMetricSpecification } = targetPolicyDescriptor;
      customizedMetricSpecification.dimensions = customizedMetricSpecification.dimensions || [];
      policy.id = p.id;
      policy.targetTrackingConfiguration = policy.targetPolicyDescriptor;
      policy.targetTrackingConfiguration.scaleOutCooldown = policy.targetTrackingConfiguration.scaleOutCooldownSec;
      policy.targetTrackingConfiguration.scaleInCooldown = policy.targetTrackingConfiguration.scaleInCooldownSec;
      return policy;
    })
    .map((p: any) => transformAwsScalingPolicy(p));
}

function sanitizeLabels(serverGroup: any): void {
  const labels = serverGroup.labels || {};
  delete labels.name;
  delete labels.source;
  delete labels.spinnakerAccount;
  delete labels[''];
  Object.keys(labels).forEach((key) => key.startsWith('titus.') && delete labels[key]);
  serverGroup.displayLabels = labels;
}

export function titusServerGroupDetailsGetter(props: IServerGroupDetailsProps, autoClose: () => void): Observable<any> {
  const { app, serverGroup } = props;
  return new Observable<any>((observer) => {
    const summary = find(app.serverGroups.data, {
      name: serverGroup.name,
      account: serverGroup.accountId,
      region: serverGroup.region,
    });

    ServerGroupReader.getServerGroup(app.name, serverGroup.accountId, serverGroup.region, serverGroup.name)
      .then(async (details: any) => {
        details.account = serverGroup.accountId;
        Object.assign(details, summary);
        sanitizeLabels(details);
        transformScalingPolicies(details);
        details.entityTagTargets = ClusterTargetBuilder.buildClusterTargets(details);

        const accountDetails = await AccountService.getAccountDetails(details.account).catch(() => null);
        const regionDetails = find(accountDetails?.regions, { name: details.region });
        details.apiEndpoint = regionDetails && (regionDetails as any).endpoint;
        details.titusUiEndpoint = details.apiEndpoint;

        if (accountDetails?.awsAccount) {
          const awsDetails = await AccountService.getAccountDetails(accountDetails.awsAccount).catch(() => null);
          if (awsDetails) {
            details.awsAccountId = awsDetails.accountId;
            details.awsEnv = awsDetails.environment;
          }
        }

        observer.next(details);
      })
      .catch((error) => {
        autoClose();
        observer.error(error);
      });
  });
}
