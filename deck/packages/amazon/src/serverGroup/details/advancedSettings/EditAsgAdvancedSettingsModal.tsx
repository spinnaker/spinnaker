import type { FormikProps } from 'formik';
import React from 'react';

import type { Application, DeckRuntimeServices, IModalComponentProps } from '@spinnaker/core';
import {
  CheckboxInput,
  confirmNotManaged,
  DeckRuntimeContext,
  FormikFormField,
  HelpField,
  NumberInput,
  ReactModal,
  ReactSelectInput,
  TaskMonitorModal,
} from '@spinnaker/core';

import type { AwsServerGroupCommandBuilder } from '../../configure/serverGroupCommandBuilder.service';
import type { IAmazonServerGroupCommand } from '../../configure/serverGroupConfiguration.service';
import type { IAmazonServerGroup } from '../../../domain';

export interface IEditAsgAdvancedSettingsModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IAmazonServerGroup;
}

function validateRequiredNonNegativeNumber(value: unknown): string | undefined {
  return typeof value !== 'number' || !Number.isFinite(value) || value < 0
    ? 'Enter a finite non-negative number'
    : undefined;
}

export class EditAsgAdvancedSettingsModal extends React.Component<IEditAsgAdvancedSettingsModalProps> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private directCommand: Partial<IAmazonServerGroupCommand>;

  public static show(props: IEditAsgAdvancedSettingsModalProps, runtimeServices: DeckRuntimeServices) {
    return confirmNotManaged(props.serverGroup, props.application).then(
      (notManaged) =>
        notManaged &&
        ReactModal.show(EditAsgAdvancedSettingsModal, props, { dialogClassName: 'modal-lg' }, runtimeServices),
    );
  }

  private get command(): Partial<IAmazonServerGroupCommand> {
    return (this.directCommand ||= this.context.services.providerServiceDelegate
      .getDelegate<AwsServerGroupCommandBuilder>('aws', 'serverGroup.commandBuilder')
      .buildUpdateServerGroupCommand(this.props.serverGroup));
  }

  private renderFields = (formik: FormikProps<IAmazonServerGroupCommand>) => {
    const { backingData } = formik.values;
    return (
      <>
        <FormikFormField
          label="Cooldown"
          name="cooldown"
          help="Seconds"
          input={(props) => <NumberInput {...props} min={0} />}
          required={true}
          validate={validateRequiredNonNegativeNumber}
        />
        <FormikFormField
          label="Enabled Metrics"
          name="enabledMetrics"
          help={<HelpField id="aws.serverGroup.enabledMetrics" />}
          input={(props) => <ReactSelectInput {...props} multi={true} stringOptions={backingData.enabledMetrics} />}
        />
        <FormikFormField
          label="Health Check Type"
          name="healthCheckType"
          input={(props) => (
            <ReactSelectInput {...props} clearable={false} stringOptions={backingData.healthCheckTypes} />
          )}
        />
        <FormikFormField
          label="Health Check Grace Period"
          name="healthCheckGracePeriod"
          help="Seconds"
          input={(props) => <NumberInput {...props} min={0} />}
          required={true}
          validate={validateRequiredNonNegativeNumber}
        />
        <FormikFormField
          label="Termination Policies"
          name="terminationPolicies"
          input={(props) => (
            <ReactSelectInput {...props} multi={true} stringOptions={backingData.terminationPolicies} />
          )}
        />
        <FormikFormField
          label="Capacity Rebalance"
          name="capacityRebalance"
          help={<HelpField id="aws.serverGroup.capacityRebalance" />}
          input={(props) => <CheckboxInput {...props} text="Enable capacity rebalance" />}
        />
      </>
    );
  };

  public render() {
    const { application, closeModal, dismissModal, serverGroup } = this.props;
    return (
      <TaskMonitorModal<IAmazonServerGroupCommand>
        application={application}
        closeModal={closeModal}
        description={`Update Advanced Settings for ${serverGroup.name}`}
        dismissModal={dismissModal}
        initialValues={this.command as IAmazonServerGroupCommand}
        mapValuesToTask={(command) => ({ application, job: [command] })}
        render={this.renderFields}
        title={`Edit Advanced Settings for ${serverGroup.name}`}
      />
    );
  }
}
