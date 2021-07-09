import { FormikProps } from 'formik';
import React from 'react';

import {
  ArtifactTypePatterns,
  IArtifact,
  IExpectedArtifact,
  IPipeline,
  IStage,
  IWizardPageComponent,
  RadioButtonInput,
  StageArtifactSelector,
} from '@spinnaker/core';
import { ICloudFoundryEnvVar } from '../../../../../domain';
import {
  Buildpacks,
  EnvironmentVariables,
  FormikConfigField,
  HealthCheck,
  InstanceParameters,
  Routes,
  Services,
} from '../../../../../presentation';

import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryManifest,
} from '../../../serverGroupConfigurationModel.cf';

export interface ICloudFoundryServerGroupConfigurationSettingsProps {
  formik: FormikProps<ICloudFoundryCreateServerGroupCommand>;
  stage: IStage;
  pipeline: IPipeline;
}

export class CloudFoundryServerGroupConfigurationSettings
  extends React.Component<ICloudFoundryServerGroupConfigurationSettingsProps>
  implements IWizardPageComponent<ICloudFoundryServerGroupConfigurationSettingsProps> {
  public static LABEL = 'Configuration';

  private defaultDirectManifest: ICloudFoundryManifest = {
    direct: {
      memory: '1024M',
      diskQuota: '1024M',
      instances: 1,
      buildpacks: [],
      healthCheckType: 'port',
      routes: [],
      environment: [],
      services: [],
    },
  };

  private excludedArtifactTypePatterns = [
    ArtifactTypePatterns.KUBERNETES,
    ArtifactTypePatterns.DOCKER_IMAGE,
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
  ];

  private manifestSourceUpdated = (source: string): void => {
    switch (source) {
      case 'artifact':
        this.props.formik.setFieldValue('manifest', {});
        this.capacityUpdated('1');
        break;
      case 'direct':
        this.props.formik.setFieldValue('manifest', this.defaultDirectManifest);
        break;
    }
  };

  private capacityUpdated = (capacity: string): void => {
    this.props.formik.setFieldValue('capacity.min', capacity);
    this.props.formik.setFieldValue('capacity.max', capacity);
    this.props.formik.setFieldValue('capacity.desired', capacity);
  };

  private getArtifactInput = (): JSX.Element => {
    const { formik, stage, pipeline } = this.props;
    const manifest = formik.values.manifest;
    return (
      <div className="form-group">
        <div className="col-md-11">
          <div className="StandardFieldLayout flex-container-h margin-between-lg">
            <div className="flex-grow">
              <StageArtifactSelector
                pipeline={pipeline}
                stage={stage}
                expectedArtifactId={manifest && manifest.artifactId}
                artifact={manifest && manifest.artifact}
                onExpectedArtifactSelected={this.onExpectedArtifactSelected}
                onArtifactEdited={this.onArtifactChanged}
                excludedArtifactTypePatterns={this.excludedArtifactTypePatterns}
                renderLabel={(field: React.ReactNode) => {
                  return <FormikConfigField label={'Artifact'}>{field}</FormikConfigField>;
                }}
              />
            </div>
          </div>
        </div>
      </div>
    );
  };

  private onExpectedArtifactSelected = (expectedArtifact: IExpectedArtifact): void => {
    this.props.formik.setFieldValue('manifest', { artifactId: expectedArtifact.id });
  };

  private onArtifactChanged = (artifact: IArtifact): void => {
    this.props.formik.setFieldValue('manifest', { artifact: artifact });
  };

  private getDirectInput = (): JSX.Element => {
    const m = this.props.formik.values.manifest.direct;
    return (
      <div>
        {
          <InstanceParameters
            diskQuotaFieldName={'manifest.direct.diskQuota'}
            instancesFieldName={'manifest.direct.instances'}
            memoryFieldName={'manifest.direct.memory'}
          />
        }
        {<Buildpacks fieldName="manifest.direct.buildpacks" />}
        {
          <HealthCheck
            healthCheckHttpEndpointFieldName={'manifest.direct.healthCheckHttpEndpoint'}
            healthCheckType={m.healthCheckType}
            healthCheckTypeFieldName={'manifest.direct.healthCheckType'}
          />
        }
        {<Routes fieldName="manifest.direct.routes" />}
        {<EnvironmentVariables fieldName="manifest.direct.environment" />}
        {<Services fieldName="manifest.direct.services" />}
      </div>
    );
  };

  public render(): JSX.Element {
    const manifest = this.props.formik.values.manifest;
    const direct = manifest && !!manifest.direct;
    return (
      <>
        <div className="form-group">
          <div className="col-md-11">
            <div className="StandardFieldLayout flex-container-h baseline margin-between-lg">
              <div className="sm-label-right">Source Type</div>
              <div className="flex-grow">
                <RadioButtonInput
                  inline={true}
                  value={direct ? 'direct' : 'artifact'}
                  options={[
                    { label: 'Artifact', value: 'artifact' },
                    { label: 'Form', value: 'direct' },
                  ]}
                  onChange={(e: any) => this.manifestSourceUpdated(e.target.value)}
                />
              </div>
            </div>
          </div>
        </div>
        {direct ? this.getDirectInput() : this.getArtifactInput()}
      </>
    );
  }

  public validate(_props: ICloudFoundryServerGroupConfigurationSettingsProps) {
    const errors = {} as any;
    const isStorageSize = (value: string) => /\d+[MG]/.test(value);

    if (!this.props.formik.values.manifest) {
      errors.manifest = 'No manifest information provided';
      return errors;
    }

    const direct = this.props.formik.values.manifest.direct;
    if (direct) {
      if (!isStorageSize(direct.memory)) {
        errors.manifest = errors.manifest || {};
        errors.manifest.memory = `Provide a size (e.g.: 256M, 1G)`;
      }
      if (!isStorageSize(direct.diskQuota)) {
        errors.manifest = errors.manifest || {};
        errors.manifest.diskQuota = `Provide a size (e.g.: 256M, 1G)`;
      }
      if (direct.routes) {
        const routeErrors = direct.routes.map((route: string) => {
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
      if (direct.environment) {
        const existingKeys: string[] = [];
        const envErrors = direct.environment.map((e: ICloudFoundryEnvVar) => {
          let myErrors: any;
          if (e.key) {
            const validKeyRegex = /^\w+$/g;
            if (!validKeyRegex.exec(e.key)) {
              myErrors = {
                key: `This field must be alphanumeric`,
              };
            } else {
              if (existingKeys.filter((key) => key === e.key).length > 0) {
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
    } else {
      const { manifest } = this.props.formik.values;
      if (
        !manifest ||
        !((manifest.artifact && manifest.artifact.type && manifest.artifact.reference) || manifest.artifactId)
      ) {
        errors.manifest = 'Manifest artifact information is required';
      }
    }

    return errors;
  }
}
