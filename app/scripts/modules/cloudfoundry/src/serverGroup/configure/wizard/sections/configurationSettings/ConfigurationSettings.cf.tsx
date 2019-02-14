import * as React from 'react';

import { Option } from 'react-select';

import {
  FormikFormField,
  HelpField,
  IArtifactAccount,
  IWizardPageComponent,
  ReactSelectInput,
  TextInput,
} from '@spinnaker/core';

import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryManifestDirectSource,
} from 'cloudfoundry/serverGroup/configure/serverGroupConfigurationModel.cf';

import { CloudFoundryRadioButtonInput } from 'cloudfoundry/presentation/forms/inputs/CloudFoundryRadioButtonInput';
import { ICloudFoundryEnvVar } from 'cloudfoundry/domain';
import {
  Buildpacks,
  EnvironmentVariables,
  HealthCheck,
  InstanceParameters,
  Routes,
  Services,
} from 'cloudfoundry/presentation';
import { FormikProps } from 'formik';

export interface ICloudFoundryServerGroupConfigurationSettingsProps {
  artifactAccounts: IArtifactAccount[];
  formik: FormikProps<ICloudFoundryCreateServerGroupCommand>;
  manifest?: any;
}

export class CloudFoundryServerGroupConfigurationSettings
  extends React.Component<ICloudFoundryServerGroupConfigurationSettingsProps>
  implements IWizardPageComponent<ICloudFoundryCreateServerGroupCommand> {
  private manifestTypeUpdated = (type: string): void => {
    switch (type) {
      case 'artifact': {
        const emptyManifestArtifact = {
          account: '',
          reference: '',
          type: 'artifact',
        };
        this.props.formik.setFieldValue('manifest', emptyManifestArtifact);
        this.capacityUpdated('1');
        break;
      }
      case 'trigger': {
        const emptyManifestTrigger = {
          account: '',
          pattern: '',
          type: 'trigger',
        };
        this.props.formik.setFieldValue('manifest', emptyManifestTrigger);
        this.capacityUpdated('1');
        break;
      }
      case 'direct': {
        const emptyManifestDirect = {
          memory: '1024M',
          diskQuota: '1024M',
          instances: 1,
          buildpacks: [] as string[],
          healthCheckType: 'port',
          healthCheckHttpEndpoint: undefined as string,
          routes: [] as string[],
          environment: [] as string[],
          services: [] as string[],
          type: 'direct',
        };
        this.props.formik.setFieldValue('manifest', emptyManifestDirect);
        break;
      }
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

  private getDirectInput = (): JSX.Element => {
    const m = this.props.formik.values.manifest as { type: string } & ICloudFoundryManifestDirectSource;
    return (
      <div>
        {
          <InstanceParameters
            diskQuotaFieldName={'manifest.diskQuota'}
            instancesFieldName={'manifest.instances'}
            memoryFieldName={'manifest.memory'}
          />
        }
        {<Buildpacks fieldName="manifest.buildpacks" />}
        {
          <HealthCheck
            healthCheckHttpEndpointFieldName={'manifest.healthCheckHttpEndpoint'}
            healthCheckType={m.healthCheckType}
            healthCheckTypeFieldName={'manifest.healthCheckType'}
          />
        }
        {<Routes fieldName="manifest.routes" />}
        {<EnvironmentVariables fieldName="manifest.environment" />}
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

  public validate(values: ICloudFoundryCreateServerGroupCommand) {
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
          const regex = /^([-\w]+)\.([-.\w]+)(:\d+)?([-/\w]+)?$/gm;
          if (route && regex.exec(route) === null) {
            return `A route did not match the expected format "host.some.domain[:9999][/some/path]"`;
          }
          return null;
        });
        if (routeErrors.some((val: string) => !!val)) {
          errors.manifest = errors.manifest || {};
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
