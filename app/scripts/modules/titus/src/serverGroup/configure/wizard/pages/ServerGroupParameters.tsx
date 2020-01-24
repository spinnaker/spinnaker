import React from 'react';
import { Field, FormikProps } from 'formik';
import Select, { Option } from 'react-select';

import {
  HelpField,
  IWizardPageComponent,
  AccountTag,
  MapEditor,
  PlatformHealthOverride,
  Application,
  ChecklistInput,
  robotToHuman,
} from '@spinnaker/core';

import { ITitusServerGroupCommand } from '../../../configure/serverGroupConfiguration.service';
import { intersection, set, union } from 'lodash';
import { enabledProcesses, processesList } from '../../../details/serviceJobProcesses/ServiceJobProcesses';
import { ITitusServiceJobProcesses } from 'titus/domain/ITitusServiceJobProcesses';

export interface IServerGroupParametersProps {
  app: Application;
  formik: FormikProps<ITitusServerGroupCommand>;
}

const migrationPolicyOptions = [
  { label: 'System Default', value: 'systemDefault' },
  { label: 'Self Managed', value: 'selfManaged' },
];

export class ServerGroupParameters extends React.Component<IServerGroupParametersProps>
  implements IWizardPageComponent<ITitusServerGroupCommand> {
  private duplicateKeys: { [name: string]: boolean } = {};

  constructor(props: IServerGroupParametersProps) {
    super(props);
  }

  public validate(_values: ITitusServerGroupCommand) {
    const { soft: softConstraints, hard: hardConstraints } = _values.constraints;
    const errors = {} as any;

    if (this.duplicateKeys.labels) {
      errors.labels = 'Job Attributes have duplicate keys.';
    }
    if (this.duplicateKeys.containerAttributes) {
      errors.containerAttributes = 'Container Attributes have duplicate keys.';
    }
    if (this.duplicateKeys.env) {
      errors.env = 'Environment Variables have duplicate keys.';
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

  private mapChanged = (key: string, values: { [key: string]: string }, duplicateKeys: boolean) => {
    this.duplicateKeys[key] = duplicateKeys;
    this.props.formik.setFieldValue(key, values);
  };

  private platformHealthOverrideChanged = (healthNames: string[]) => {
    this.props.formik.setFieldValue('interestingHealthProviderNames', healthNames);
  };

  public render() {
    const { app } = this.props;
    const { setFieldValue, values } = this.props.formik;

    return (
      <>
        <div className="form-group">
          <div className="col-md-4 sm-label-right">
            <b>IAM Instance Profile </b>
            <HelpField id="titus.deploy.iamProfile" />
          </div>
          <div className="col-md-5">
            <Field type="text" className="form-control input-sm no-spel" name="iamProfile" />
          </div>
          <div className="col-md-1 small" style={{ whiteSpace: 'nowrap', paddingLeft: '0px', paddingTop: '7px' }}>
            in{' '}
            <AccountTag
              account={
                values.backingData.credentialsKeyedByAccount[values.credentials] &&
                values.backingData.credentialsKeyedByAccount[values.credentials].awsAccount
              }
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-4 sm-label-right">
            <b>Capacity Group </b>
            <HelpField id="titus.deploy.capacityGroup" />
          </div>
          <div className="col-md-6">
            <Field type="text" name="capacityGroup" className="form-control input-sm no-spel" />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-4 sm-label-right">
            <b>Migration Policy </b>
            <HelpField id="titus.deploy.migrationPolicy" />
          </div>
          <div className="col-md-4">
            <Select
              value={values.migrationPolicy.type}
              options={migrationPolicyOptions}
              onChange={(option: Option<string>) =>
                setFieldValue('migrationPolicy', { ...values.migrationPolicy, ...{ type: option.value } })
              }
              clearable={false}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-4 sm-label-right">
            <b>Service Job Processes </b>
          </div>
          <div className="col-md-4">
            <ChecklistInput
              value={enabledProcesses(values.serviceJobProcesses)}
              options={union(processesList, Object.keys(values.serviceJobProcesses)).map((value: string) => ({
                value,
                label: robotToHuman(value),
              }))}
              onChange={(e: React.ChangeEvent<any>) =>
                setFieldValue(
                  'serviceJobProcesses',
                  union(processesList, Object.keys(values.serviceJobProcesses)).reduce(
                    (processes, process: string) => set(processes, process, !!e.target.value.includes(process)),
                    {} as ITitusServiceJobProcesses,
                  ),
                )
              }
            />
          </div>
        </div>
        <hr />
        <div className="form-group">
          <h4 className="col-sm-12">
            <b>Soft Constraints </b>
            <HelpField id="titus.deploy.softConstraints" />
          </h4>
          <div className="col-sm-12">
            <MapEditor
              model={values.constraints.soft}
              allowEmpty={true}
              onChange={(v: any, d) => this.mapChanged('constraints.soft', v, d)}
            />
          </div>
        </div>
        <div className="form-group">
          <h4 className="col-sm-12">
            <b>Hard Constraints </b>
            <HelpField id="titus.deploy.hardConstraints" />
          </h4>
          <div className="col-sm-12">
            <MapEditor
              model={values.constraints.hard}
              allowEmpty={true}
              onChange={(v: any, d) => this.mapChanged('constraints.hard', v, d)}
            />
          </div>
        </div>
        <hr />
        <div className="form-group">
          <h4 className="col-sm-12">
            <b>Job Attributes</b>
          </h4>
          <div className="col-sm-12">
            <MapEditor
              model={values.labels}
              allowEmpty={true}
              onChange={(v: any, d) => this.mapChanged('labels', v, d)}
            />
          </div>
        </div>
        <div className="form-group">
          <h4 className="col-sm-12">
            <b>Container Attributes</b>
          </h4>
          <div className="col-sm-12">
            <MapEditor
              model={values.containerAttributes}
              allowEmpty={true}
              onChange={(v: any, d) => this.mapChanged('containerAttributes', v, d)}
            />
          </div>
        </div>
        <div className="form-group">
          <h4 className="col-sm-12">
            <b>Environment Variables</b>
          </h4>
          <div className="col-sm-12">
            <MapEditor model={values.env} allowEmpty={true} onChange={(v: any, d) => this.mapChanged('env', v, d)} />
          </div>
        </div>
        {app.attributes.platformHealthOnlyShowOverride && (
          <div className="form-group">
            <div className="col-md-5 sm-label-right">
              <b>Task Completion</b>
            </div>
            <div className="col-md-6">
              <PlatformHealthOverride
                interestingHealthProviderNames={values.interestingHealthProviderNames}
                platformHealthType="Titus"
                onChange={this.platformHealthOverrideChanged}
              />
            </div>
          </div>
        )}
      </>
    );
  }
}
