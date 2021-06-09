import { FormikProps } from 'formik';
import { intersection, set, union } from 'lodash';
import React from 'react';

import {
  AccountTag,
  Application,
  CheckboxInput,
  ChecklistInput,
  FormikFormField,
  FormValidator,
  HelpField,
  IFormInputProps,
  IWizardPageComponent,
  LayoutProvider,
  MapEditorInput,
  PlatformHealthOverrideInput,
  robotToHuman,
  SelectInput,
  TextInput,
  useFormInputValueMapper,
} from '@spinnaker/core';

import { TitusMapLayout } from './TitusMapLayout';
import { ITitusServerGroupCommand } from '../../../configure/serverGroupConfiguration.service';
import { processesList } from '../../../details/serviceJobProcesses/ServiceJobProcesses';

import './ServerGroupParameters.less';

export interface IServerGroupParametersProps {
  app: Application;
  formik: FormikProps<ITitusServerGroupCommand>;
}

const migrationPolicyOptions = [
  { label: 'System Default', value: 'systemDefault' },
  { label: 'Self Managed', value: 'selfManaged' },
];

function ServiceJobProcessesInput(props: IFormInputProps) {
  const allProcesses = union(processesList, Object.keys(props.value));
  // Map to a list of strings (formik -> input)
  const mapToInputValue = (value: Record<string, boolean>) =>
    Object.entries(value)
      .filter(([_key, val]) => val === true)
      .map(([key, _val]) => key);

  // Map to an Object, but include all known processes (input -> formik)
  const mapFromInputValue = (value: string[]): Record<string, boolean> =>
    allProcesses.reduce((acc, process) => {
      acc[process] = value.includes(process);
      return acc;
    }, {} as Record<string, boolean>);

  const mappedProps = useFormInputValueMapper(props, mapToInputValue, mapFromInputValue);

  const options = allProcesses.map((process) => {
    return { label: robotToHuman(process), value: process };
  });

  return <ChecklistInput {...mappedProps} options={options} />;
}

export function IPv6CheckboxInput(props: IFormInputProps) {
  const mappedProps = useFormInputValueMapper(
    props,
    (val: string) => val === 'true', // formik -> checkbox
    (_val, e) => (e.target.checked ? 'true' : 'false'), // checkbox -> formik
  );
  return <CheckboxInput {...mappedProps} />;
}

const IamInstanceProfileInput = (props: IFormInputProps & { awsAccount: string; setDefaultIamProfile: () => void }) => {
  const { awsAccount, setDefaultIamProfile, ...inputProps } = props;
  return (
    <div className="flex-container-h baseline margin-between-md">
      <TextInput {...inputProps} />
      {props.value ? (
        <>
          <span>in</span>
          <AccountTag account={awsAccount} />
        </>
      ) : (
        <button className="link" style={{ whiteSpace: 'nowrap' }} onClick={setDefaultIamProfile}>
          Apply default value
        </button>
      )}
    </div>
  );
};

export class ServerGroupParameters
  extends React.Component<IServerGroupParametersProps>
  implements IWizardPageComponent<ITitusServerGroupCommand> {
  constructor(props: IServerGroupParametersProps) {
    super(props);
  }

  public validate(_values: ITitusServerGroupCommand) {
    const validator = new FormValidator(_values);
    validator.field('iamProfile', 'IAM Instance Profile').required();

    const errors = validator.validateForm();

    const duplicates = intersection(Object.keys(_values.constraints.soft), Object.keys(_values.constraints.hard));
    duplicates.forEach((key) => {
      set(errors, `constraints.soft.${key}`, `Constraint '${key}' must be either soft or hard, not both.`);
      set(errors, `constraints.hard.${key}`, `Constraint '${key}' must be either soft or hard, not both.`);
    });

    Object.keys(_values.env || {})
      .filter((key) => !key.startsWith('__MapEditorDuplicateKey'))
      .forEach((key) => {
        if (!key.match(/^[A-Za-z_].*/)) {
          set(errors, `env.${key}`, 'Environment variable names must start with a letter or underscore');
        } else if (!key.match(/[A-Za-z_][a-zA-Z0-9_]*$/)) {
          set(errors, `env.${key}`, 'Environment variable names must contain only letter, numbers, or underscores');
        }
      });

    return errors;
  }

  public render() {
    const { app } = this.props;
    const { setFieldValue, values } = this.props.formik;

    const setDefaultIamProfile = () => setFieldValue('iamProfile', values.viewState.defaultIamProfile);
    return (
      <div className="ServerGroupParameters">
        <FormikFormField
          name="iamProfile"
          label="IAM Instance Profile"
          help={<HelpField id="titus.deploy.iamProfile" />}
          input={(props) => (
            <IamInstanceProfileInput
              {...props}
              awsAccount={values.backingData.credentialsKeyedByAccount[values.credentials]?.awsAccount}
              setDefaultIamProfile={setDefaultIamProfile}
            />
          )}
        />

        <FormikFormField
          name="capacityGroup"
          label="Capacity Group"
          help={<HelpField id="titus.deploy.capacityGroup" />}
          input={(props) => <TextInput {...props} />}
        />

        <FormikFormField
          name="migrationPolicy.type"
          label="Migration Policy"
          help={<HelpField id="titus.deploy.migrationPolicy" />}
          input={(props) => <SelectInput options={migrationPolicyOptions} {...props} />}
        />

        <FormikFormField
          name="serviceJobProcesses"
          label="Service Job Processes"
          input={(props) => <ServiceJobProcessesInput {...props} />}
        />

        <FormikFormField
          name="containerAttributes['titusParameter.agent.assignIPv6Address']"
          label="Associate IPv6 Address (Recommended)"
          help={<HelpField id="serverGroup.ipv6" />}
          input={(props) => <IPv6CheckboxInput {...props} />}
        />

        <hr />

        <LayoutProvider value={TitusMapLayout}>
          <FormikFormField
            name="constraints.soft"
            label="Soft Constraints "
            help={<HelpField id="titus.deploy.softConstraints" />}
            input={(props) => <MapEditorInput allowEmptyValues={true} {...props} />}
          />

          <FormikFormField
            name="constraints.hard"
            label="Hard Constraints"
            help={<HelpField id="titus.deploy.hardConstraints" />}
            input={(props) => <MapEditorInput allowEmptyValues={true} {...props} />}
          />

          <hr />

          <FormikFormField
            name="labels"
            label="Job Attributes"
            input={(props) => <MapEditorInput allowEmptyValues={true} {...props} />}
          />

          <FormikFormField
            name="containerAttributes"
            label="Container Attributes"
            input={(props) => <MapEditorInput allowEmptyValues={true} {...props} />}
          />

          <FormikFormField
            name="env"
            label="Environment Variables"
            input={(props) => <MapEditorInput allowEmptyValues={true} {...props} />}
          />
        </LayoutProvider>

        {app.attributes.platformHealthOnlyShowOverride && (
          <FormikFormField
            name="interestingHealthProviderNames"
            label="Task Completion"
            input={(props) => <PlatformHealthOverrideInput {...props} platformHealthType="Titus" />}
          />
        )}
      </div>
    );
  }
}
