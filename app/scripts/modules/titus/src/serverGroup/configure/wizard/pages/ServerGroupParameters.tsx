import { FormikProps } from 'formik';
import { intersection, union } from 'lodash';
import React from 'react';

import {
  AccountTag,
  Application,
  CheckboxInput,
  ChecklistInput,
  FormikFormField,
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

function IPv6CheckboxInput(props: IFormInputProps) {
  const mappedProps = useFormInputValueMapper(
    props,
    (val: string) => val === 'true', // formik -> checkbox
    (_val, e) => (e.target.checked ? 'true' : undefined), // checkbox -> formik
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
    const { soft: softConstraints, hard: hardConstraints } = _values.constraints;
    const errors = {} as any;

    if (!_values.iamProfile) {
      errors.iamProfile = 'IAM Profile is required.';
    }

    const duplicateConstraints = intersection(Object.keys(softConstraints), Object.keys(hardConstraints));
    if (duplicateConstraints.length > 0) {
      errors.constraints = errors.constraints || {};
      errors.constraints.soft = errors.constraints.hard = `${duplicateConstraints.join(
        ',',
      )} constraints must be either soft or hard, not both.`;
    }

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
          label="Associate IPv6 Address"
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
