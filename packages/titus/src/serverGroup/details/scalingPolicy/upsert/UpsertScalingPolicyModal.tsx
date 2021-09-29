import { cloneDeep } from 'lodash';
import * as React from 'react';

import type {
  IScalingPolicy,
  IStepAdjustment,
  IStepPolicyDescription,
  ITargetTrackingPolicy,
  IUpsertScalingPolicyCommand,
} from '@spinnaker/amazon';
import { AlarmConfigurer, ScalingPolicyAdditionalSettings, StepPolicyAction } from '@spinnaker/amazon';
import type { IAmazonServerGroup, IScalingPolicyAlarm } from '@spinnaker/amazon/src/domain';
import type { Application, IModalComponentProps } from '@spinnaker/core';
import { TaskMonitorModal } from '@spinnaker/core';

import { TitusScalingPolicyCommandBuilder } from './TitusScalingPolicyCommandBuilderService';
import type { ITitusServerGroup } from '../../../../domain';

type Operator = 'Add' | 'Remove' | 'Set to';
type AdjustmentTypeView = 'instances' | 'percent of group';

export interface IUpsertScalingPolicyModalProps extends IModalComponentProps {
  app: Application;
  policy: IScalingPolicy;
  serverGroup: ITitusServerGroup;
}

export const UpsertScalingPolicyModal = ({
  app,
  closeModal,
  dismissModal,
  policy,
  serverGroup,
}: IUpsertScalingPolicyModalProps) => {
  const [command, setCommand] = React.useState<IUpsertScalingPolicyCommand>({} as IUpsertScalingPolicyCommand);
  React.useEffect(() => {
    const baseCommand = TitusScalingPolicyCommandBuilder.buildNewCommand(
      'Step',
      serverGroup,
      policy as ITargetTrackingPolicy,
    );
    setCommand(baseCommand);
  }, []);

  const adjustmentBasis = policy.stepAdjustments?.length
    ? policy.stepAdjustments[0].scalingAdjustment
    : policy.scalingAdjustment;
  const [action, setAction] = React.useState<Operator>(
    command.adjustmentType === 'ExactCapacity' ? 'Set to' : adjustmentBasis > 0 ? 'Add' : 'Remove',
  );
  const adjustmentTypeView =
    command.adjustmentType === 'ExactCapacity' || command.adjustmentType === 'ChangeInCapacity'
      ? 'instances'
      : 'percent of group';

  const mode = !policy.id ? 'Create' : 'Edit';
  const comparatorBound = command?.alarm?.comparisonOperator?.indexOf('Greater') === 0 ? 'max' : 'min';

  const boundsChanged = (step: IStepPolicyDescription) => {
    const source = comparatorBound === 'min' ? 'metricIntervalLowerBound' : 'metricIntervalUpperBound';
    const target = source === 'metricIntervalLowerBound' ? 'metricIntervalUpperBound' : 'metricIntervalLowerBound';

    const adjustments = step.stepAdjustments;
    (adjustments || []).forEach((a, index) => {
      if (adjustments.length > index + 1) {
        adjustments[index + 1][target] = a[source];
      }
    });
    // Remove the source boundary from the last step
    delete adjustments[adjustments.length - 1][source];
  };

  const stepsChanged = (newSteps: IStepAdjustment[]) => {
    const newCommand = cloneDeep(command);
    newCommand.step.stepAdjustments = newSteps;
    boundsChanged(newCommand.step);
    setCommand(newCommand);
  };

  const adjustmentTypeChanged = (action: Operator, type: AdjustmentTypeView) => {
    setAction(action);

    const newType =
      type !== 'instances' ? 'PercentChangeInCapacity' : action === 'Set to' ? 'ExactCapacity' : 'ChangeInCapacity';
    setCommand({
      ...command,
      adjustmentType: newType,
    });
  };

  return (
    <TaskMonitorModal<IUpsertScalingPolicyCommand>
      closeModal={closeModal}
      dismissModal={dismissModal}
      title={`${mode} scaling policy`}
      application={app}
      description={`${mode} scaling policy for ${serverGroup.name}`}
      initialValues={command}
      mapValuesToTask={() => {
        const preppedValues = TitusScalingPolicyCommandBuilder.prepareCommandForUpsert(command, action === 'Remove');
        return {
          application: app,
          job: [
            {
              type: preppedValues.type || 'upsertScalingPolicy',
              ...preppedValues,
            },
          ],
        };
      }}
      render={() => (
        <div>
          <h4 className="section-heading">Conditions</h4>
          <div className="section-body">
            <p>
              <b>Note:</b> metrics must be sent to Amazon CloudWatch before they can be used in auto scaling. If you do
              not see a metric below, click "Configure available metrics" in the server group details to set up
              forwarding from Atlas to CloudWatch.
            </p>
            <AlarmConfigurer
              alarm={command.alarm}
              multipleAlarms={Boolean(policy?.alarms?.length > 1)}
              serverGroup={(serverGroup as unknown) as IAmazonServerGroup}
              stepAdjustments={command.step.stepAdjustments}
              stepsChanged={stepsChanged}
              updateAlarm={(alarm: IScalingPolicyAlarm) =>
                setCommand({ ...command, alarm } as IUpsertScalingPolicyCommand)
              }
            />
          </div>
          <h4 className="section-heading">Actions</h4>
          <div className="section-body">
            {!command.alarm?.metricName && <h4 className="text-center">Select a metric</h4>}
            {command.alarm?.metricName && (
              <StepPolicyAction
                adjustmentType={adjustmentTypeView}
                adjustmentTypeChanged={adjustmentTypeChanged}
                alarm={command.alarm}
                isMin={comparatorBound === 'min'}
                operator={action}
                step={command.step}
                stepsChanged={stepsChanged}
              />
            )}
          </div>
          <ScalingPolicyAdditionalSettings
            command={command}
            isInstanceType={adjustmentTypeView === 'instances'}
            isNew={Boolean(!policy.policyARN)}
            operator={action}
            updateCommand={setCommand}
          />
        </div>
      )}
    />
  );
};
