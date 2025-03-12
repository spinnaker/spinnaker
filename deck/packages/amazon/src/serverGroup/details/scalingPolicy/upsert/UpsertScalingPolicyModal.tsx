import { cloneDeep } from 'lodash';
import * as React from 'react';

import type { Application, IModalComponentProps } from '@spinnaker/core';
import { TaskMonitorModal } from '@spinnaker/core';

import { ScalingPolicyAdditionalSettings } from './ScalingPolicyAdditionalSettings';
import { ScalingPolicyCommandBuilder } from './ScalingPolicyCommandBuilderService';
import type { IStepPolicyDescription, IUpsertScalingPolicyCommand } from '../ScalingPolicyWriter';
import { AlarmConfigurer } from './alarm/AlarmConfigurer';
import type { IAmazonServerGroup, IScalingPolicy, IStepAdjustment, ITargetTrackingPolicy } from '../../../../domain';
import { SimplePolicyAction } from './simple/SimplePolicyAction';
import type { AdjustmentTypeView, Operator } from './step/StepPolicyAction';
import { StepPolicyAction } from './step/StepPolicyAction';

export interface IUpsertScalingPolicyModalProps extends IModalComponentProps {
  app: Application;
  policy: IScalingPolicy;
  serverGroup: IAmazonServerGroup;
}

export const UpsertScalingPolicyModal = ({
  app,
  closeModal,
  dismissModal,
  policy,
  serverGroup,
}: IUpsertScalingPolicyModalProps) => {
  const modalProps = { closeModal, dismissModal };
  const [command, setCommand] = React.useState<IUpsertScalingPolicyCommand>({} as IUpsertScalingPolicyCommand);
  React.useEffect(() => {
    const baseCommand = ScalingPolicyCommandBuilder.buildNewCommand(
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

  const isStep = command.step;
  const mode = !policy.policyARN ? 'Create' : 'Edit';
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

  const scalingAdjustmentChanged = (adjustment: number) => {
    const newCommand = cloneDeep(command);
    newCommand.simplescalingAdjustment = adjustment;
    setCommand(newCommand);
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

  const toggleMode = () => {
    const updatedCommand = cloneDeep(command);
    const cooldownOrWarmup = command.step ? command.step.estimatedInstanceWarmup : command.simple.cooldown;

    if (command.step) {
      delete updatedCommand.step;
      updatedCommand.simple = ScalingPolicyCommandBuilder.buildSimplePolicy({
        cooldown: cooldownOrWarmup,
      } as IScalingPolicy);
    } else {
      const stepAdjustments = [
        {
          scalingAdjustment: command.simple.scalingAdjustment,
        },
      ] as IStepAdjustment[];
      if (comparatorBound === 'min') {
        stepAdjustments[0].metricIntervalUpperBound = 0;
      } else {
        stepAdjustments[0].metricIntervalLowerBound = 0;
      }
      const stepPolicy = {
        estimatedInstanceWarmup: cooldownOrWarmup,
        stepAdjustments: stepAdjustments,
      };
      delete updatedCommand.simple;
      updatedCommand.step = ScalingPolicyCommandBuilder.buildStepPolicy(
        stepPolicy as IScalingPolicy,
        updatedCommand.alarm.threshold,
        cooldownOrWarmup,
      );

      boundsChanged(updatedCommand.step);
    }

    setCommand(updatedCommand);
  };

  return (
    <TaskMonitorModal<IUpsertScalingPolicyCommand>
      {...modalProps}
      title={`${mode} scaling policy`}
      application={app}
      description={`${mode} scaling policy for ${serverGroup.name}`}
      initialValues={command}
      mapValuesToTask={() => {
        const preppedValues = ScalingPolicyCommandBuilder.prepareCommandForUpsert(command, action === 'Remove');
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
            <AlarmConfigurer
              alarm={command.alarm}
              multipleAlarms={Boolean(policy?.alarms?.length > 1)}
              serverGroup={serverGroup}
              stepAdjustments={command.step.stepAdjustments}
              stepsChanged={stepsChanged}
              updateAlarm={(alarm) => setCommand({ ...command, alarm } as IUpsertScalingPolicyCommand)}
            />
          </div>
          <h4 className="section-heading">Actions</h4>
          <div className="section-body">
            {!command.alarm?.metricName && <h4 className="text-center">Select a metric</h4>}
            {command.alarm?.metricName && !isStep && (
              <div>
                <div className="row">
                  <div className="col-md-10 col-md-offset-1">
                    <p>
                      This is a simple scaling policy. To declare different actions based on the magnitude of the alarm,
                      <b>
                        switch to a
                        <a className="clickable sp-margin-xs-l" onClick={toggleMode}>
                          step policy
                        </a>
                        .
                      </b>
                    </p>
                  </div>
                </div>
                <SimplePolicyAction
                  adjustmentType={adjustmentTypeView}
                  adjustmentTypeChanged={adjustmentTypeChanged}
                  operator={action}
                  scalingAdjustment={command.simple?.scalingAdjustment}
                  updateScalingAdjustment={scalingAdjustmentChanged}
                />
              </div>
            )}
            {command.alarm?.metricName && isStep && (
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
