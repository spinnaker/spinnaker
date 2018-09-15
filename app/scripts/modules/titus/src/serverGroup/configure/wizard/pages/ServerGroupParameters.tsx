import * as React from 'react';
import { FormikProps, Field } from 'formik';
import Select, { Option } from 'react-select';

import {
  IWizardPageProps,
  wizardPage,
  HelpField,
  AccountTag,
  MapEditor,
  PlatformHealthOverride,
  Application,
} from '@spinnaker/core';

import { ITitusServerGroupCommand, Constraint } from '../../../configure/serverGroupConfiguration.service';

export interface IServerGroupParametersProps {
  app: Application;
}

export interface IServerGroupParametersState {
  hardConstraintOptions: Array<Option<Constraint>>;
  softConstraintOptions: Array<Option<Constraint>>;
}

const constraintOptions: Array<Option<Constraint>> = [
  { label: 'ExclusiveHost', value: 'ExclusiveHost' },
  { label: 'UniqueHost', value: 'UniqueHost' },
  { label: 'ZoneBalance', value: 'ZoneBalance' },
];

const migrationPolicyOptions = [
  { label: 'System Default', value: 'systemDefault' },
  { label: 'Self Managed', value: 'selfManaged' },
];

class ServerGroupParametersImpl extends React.Component<
  IServerGroupParametersProps & IWizardPageProps & FormikProps<ITitusServerGroupCommand>,
  IServerGroupParametersState
> {
  public static LABEL = 'Advanced Settings';

  private duplicateKeys: { [name: string]: boolean } = {};

  constructor(props: IServerGroupParametersProps & IWizardPageProps & FormikProps<ITitusServerGroupCommand>) {
    super(props);

    this.state = this.getConstraints(props.values);
  }

  public validate(_values: ITitusServerGroupCommand): { [key: string]: string } {
    const errors: { [key: string]: string } = {};

    if (this.duplicateKeys.labels) {
      errors.labels = 'Job Attributes have duplicate keys.';
    }
    if (this.duplicateKeys.containerAttributes) {
      errors.containerAttributes = 'Container Attributes have duplicate keys.';
    }
    if (this.duplicateKeys.env) {
      errors.env = 'Environment Variables have duplicate keys.';
    }

    return errors;
  }

  private mapChanged = (key: string, values: { [key: string]: string }, duplicateKeys: boolean) => {
    this.duplicateKeys[key] = duplicateKeys;
    this.props.setFieldValue(key, values);
  };

  private platformHealthOverrideChanged = (healthNames: string[]) => {
    this.props.setFieldValue('interestingHealthProviderNames', healthNames);
  };

  private getConstraints = (values: ITitusServerGroupCommand) => {
    return {
      hardConstraintOptions: constraintOptions.filter(o => !values.softConstraints.includes(o.value as any)),
      softConstraintOptions: constraintOptions.filter(o => !values.hardConstraints.includes(o.value as any)),
    };
  };

  private updateConstraints = (name: string, values: Constraint[]) => {
    this.props.setFieldValue(name, values);
    (this.props.values as any)[name] = values;
    this.setState(this.getConstraints(this.props.values));
  };

  public render() {
    const { app, setFieldValue, values } = this.props;
    const { softConstraintOptions, hardConstraintOptions } = this.state;
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
            in <AccountTag account={values.backingData.credentialsKeyedByAccount[values.credentials].awsAccount} />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-4 sm-label-right">
            <b>Soft Constraints </b>
            <HelpField id="titus.deploy.hardConstraints" />
          </div>
          <div className="col-md-5">
            <Select
              multi={true}
              value={values.softConstraints}
              options={softConstraintOptions}
              onChange={(option: Array<Option<Constraint>>) =>
                this.updateConstraints('softConstraints', option.map(o => o.value))
              }
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-4 sm-label-right">
            <b>Hard Constraints </b>
            <HelpField id="titus.deploy.hardConstraints" />
          </div>
          <div className="col-md-5">
            <Select
              multi={true}
              value={values.hardConstraints}
              options={hardConstraintOptions}
              onChange={(option: Array<Option<Constraint>>) =>
                this.updateConstraints('hardConstraints', option.map(o => o.value))
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
              onChange={(option: Option<String>) =>
                setFieldValue('migrationPolicy', { ...values.migrationPolicy, ...{ type: option.value } })
              }
              clearable={false}
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
                command={values}
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

export const ServerGroupParameters = wizardPage<IServerGroupParametersProps>(ServerGroupParametersImpl);
