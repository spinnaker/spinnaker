import * as React from 'react';

import { Option } from 'react-select';

import {
  FormikFormField,
  HelpField,
  IArtifactAccount,
  IWizardPageProps,
  ReactSelectInput,
  TextInput,
  wizardPage,
} from '@spinnaker/core';

import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryManifestDirectSource,
} from 'cloudfoundry/serverGroup/configure/serverGroupConfigurationModel.cf';

import { CloudFoundryRadioButtonInput } from 'cloudfoundry/presentation/forms/inputs/CloudFoundryRadioButtonInput';
import { ICloudFoundryEnvVar } from 'cloudfoundry/domain';
import { Buildpacks } from 'cloudfoundry/serverGroup/configure/wizard/sections/configurationSettings/Buildpacks';
import { Routes } from 'cloudfoundry/serverGroup/configure/wizard/sections/configurationSettings/Routes';
import { Services } from 'cloudfoundry/serverGroup/configure/wizard/sections/configurationSettings/Services';
import { EnvironmentVariables } from 'cloudfoundry/serverGroup/configure/wizard/sections/configurationSettings/EnvironmentVariables';

export interface ICloudFoundryServerGroupConfigurationSettingsProps
  extends IWizardPageProps<ICloudFoundryCreateServerGroupCommand> {
  artifactAccounts: IArtifactAccount[];
  manifest?: any;
}

class ConfigurationSettingsImpl extends React.Component<ICloudFoundryServerGroupConfigurationSettingsProps> {
  public static LABEL = 'Configuration';

  private manifestTypeUpdated = (type: string): void => {
    switch (type) {
      case 'artifact':
        this.props.formik.setFieldValue('manifest.account', '');
        this.props.formik.setFieldValue('manifest.reference', '');
        this.capacityUpdated('1');
        break;
      case 'trigger':
        this.props.formik.setFieldValue('manifest.account', '');
        this.props.formik.setFieldValue('manifest.pattern', '');
        this.capacityUpdated('1');
        break;
      case 'direct':
        this.props.formik.setFieldValue('manifest.memory', '1024M');
        this.props.formik.setFieldValue('manifest.diskQuota', '1024M');
        this.props.formik.setFieldValue('manifest.instances', 1);
        this.props.formik.setFieldValue('manifest.buildpacks', []);
        this.props.formik.setFieldValue('manifest.healthCheckType', 'port');
        this.props.formik.setFieldValue('manifest.healthCheckHttpEndpoint', undefined);
        this.props.formik.setFieldValue('manifest.routes', []);
        this.props.formik.setFieldValue('manifest.environment', []);
        this.props.formik.setFieldValue('manifest.services', []);
        break;
    }
  };

  private capacityUpdated = (capacity: string): void => {
    this.props.formik.setFieldValue('capacity.min', capacity);
    this.props.formik.setFieldValue('capacity.max', capacity);
    this.props.formik.setFieldValue('capacity.desired', capacity);
  };

  private getArtifactInput = (): JSX.Element => {
    const { artifactAccounts } = this.props;
    return (
      <div className="form-group">
        <div className="col-md-9">
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name="manifest.account"
              label="Artifact Account"
              fastField={false}
              input={props => (
                <ReactSelectInput
                  {...props}
                  inputClassName="cloudfoundry-react-select"
                  stringOptions={artifactAccounts && artifactAccounts.map((acc: IArtifactAccount) => acc.name)}
                  clearable={false}
                />
              )}
              required={true}
            />
          </div>
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name="manifest.reference"
              label="Reference"
              input={props => <TextInput {...props} />}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  };

  private getTriggerInput = (): JSX.Element => {
    const { artifactAccounts } = this.props;

    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="manifest.pattern"
            label="Artifact Pattern"
            input={props => <TextInput {...props} />}
            required={true}
          />
        </div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="manifest.account"
            label="Artifact Account"
            fastField={false}
            input={props => (
              <ReactSelectInput
                {...props}
                stringOptions={artifactAccounts && artifactAccounts.map((acc: IArtifactAccount) => acc.name)}
                clearable={false}
              />
            )}
            help={<HelpField id="cf.manifest.trigger.account" />}
            required={true}
          />
        </div>
      </div>
    );
  };

  private getContainerInput = (): JSX.Element => {
    return (
      <div>
        <div className="col-md-9">
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name="manifest.memory"
              fastField={false}
              input={props => <TextInput {...props} />}
              label="Memory"
              required={true}
            />
          </div>
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name="manifest.diskQuota"
              fastField={false}
              input={props => <TextInput {...props} />}
              label="Disk Quota"
              required={true}
            />
          </div>
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name="manifest.instances"
              fastField={false}
              input={props => <TextInput type="number" {...props} />}
              label="Instances"
              required={true}
            />
          </div>
        </div>
        <Buildpacks />
      </div>
    );
  };

  private getHealthCheckInput = (): JSX.Element => {
    const manifest = this.props.formik.values.manifest as { type: string } & ICloudFoundryManifestDirectSource;
    const HEALTH_CHECK_TYPE_OPTIONS = [
      { label: 'port', value: 'port' },
      { label: 'HTTP', value: 'http' },
      { label: 'process', value: 'process' },
    ];
    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="manifest.healthCheckType"
            label="Health Check Type"
            fastField={false}
            input={props => <CloudFoundryRadioButtonInput {...props} options={HEALTH_CHECK_TYPE_OPTIONS} />}
          />
        </div>
        {manifest.healthCheckType === 'http' && (
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name="manifest.healthCheckHttpEndpoint"
              label="Endpoint"
              input={props => <TextInput {...props} required={true} />}
            />
          </div>
        )}
      </div>
    );
  };

  private getDirectInput = (): JSX.Element => {
    return (
      <div>
        {this.getContainerInput()}
        {this.getHealthCheckInput()}
        {<Routes />}
        {<EnvironmentVariables />}
        {<Services />}
      </div>
    );
  };

  private getManifestTypeOptions(): Array<Option<string>> {
    if (this.props.formik.values.viewState.mode === 'pipeline') {
      return [
        { label: 'Artifact', value: 'artifact' },
        { label: 'Trigger', value: 'trigger' },
        { label: 'Form', value: 'direct' },
      ];
    } else {
      return [{ label: 'Artifact', value: 'artifact' }, { label: 'Form', value: 'direct' }];
    }
  }

  private getManifestContentInput(): JSX.Element {
    switch (this.props.formik.values.manifest.type) {
      case 'direct':
        return this.getDirectInput();
      case 'trigger':
        return this.getTriggerInput();
      default:
        return this.getArtifactInput();
    }
  }

  public render(): JSX.Element {
    return (
      <div>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name="manifest.type"
            label="Source Type"
            input={props => <CloudFoundryRadioButtonInput {...props} options={this.getManifestTypeOptions()} />}
            onChange={this.manifestTypeUpdated}
          />
        </div>
        {this.getManifestContentInput()}
      </div>
    );
  }

  public validate(values: ICloudFoundryServerGroupConfigurationSettingsProps) {
    const errors = {} as any;
    const isStorageSize = (value: string) => /\d+[MG]/.test(value);
    if (values.manifest.type === 'direct') {
      if (!isStorageSize(values.manifest.memory)) {
        errors.manifest = errors.manifest || {};
        errors.manifest.memory = `Provide a size (e.g.: 256M, 1G)`;
      }
      if (!isStorageSize(values.manifest.diskQuota)) {
        errors.manifest = errors.manifest || {};
        errors.manifest.diskQuota = `Provide a size (e.g.: 256M, 1G)`;
      }
      if (values.manifest.routes) {
        const routeErrors = values.manifest.routes.map((route: string) => {
          const regex = /^([a-zA-Z0-9_-]+)\.([a-zA-Z0-9_.-]+)(:[0-9]+)?([\/a-zA-Z0-9_-]+)?$/gm;
          if (route && regex.exec(route) === null) {
            return `A route did not match the expected format "host.some.domain[:9999][/some/path]"`;
          }
          return null;
        });
        if (routeErrors.some((val: string) => !!val)) {
          errors.manifest.routes = routeErrors;
        }
      }
      if (values.manifest.environment) {
        const existingKeys: string[] = [];
        const envErrors = values.manifest.environment.map((e: ICloudFoundryEnvVar) => {
          let myErrors: any;
          if (e.key) {
            const validKeyRegex = /^\w+$/g;
            if (!validKeyRegex.exec(e.key)) {
              myErrors = {
                key: `This field must be alphanumeric`,
              };
            } else {
              if (existingKeys.filter(key => key === e.key).length > 0) {
                myErrors = {
                  key: `Duplicate variable name`,
                };
              } else {
                existingKeys.push(e.key);
              }
            }
          }
          return myErrors;
        });
        if (envErrors.some((val: string) => !!val)) {
          errors.manifest = errors.manifest || {};
          errors.manifest.environment = envErrors;
        }
      }
    }

    return errors;
  }
}

export const CloudFoundryServerGroupConfigurationSettings = wizardPage(ConfigurationSettingsImpl);
