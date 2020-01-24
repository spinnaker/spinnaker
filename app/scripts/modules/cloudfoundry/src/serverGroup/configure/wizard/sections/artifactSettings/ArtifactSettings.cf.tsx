import React from 'react';

import { FormikProps } from 'formik';

import {
  IArtifact,
  IExpectedArtifact,
  IWizardPageComponent,
  IPipeline,
  IStage,
  StageArtifactSelector,
  ArtifactTypePatterns,
} from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from '../../../serverGroupConfigurationModel.cf';
import { FormikConfigField } from 'cloudfoundry/presentation';

export interface ICloudFoundryCreateServerGroupArtifactSettingsProps {
  formik: FormikProps<ICloudFoundryCreateServerGroupCommand>;
  stage: IStage;
  pipeline: IPipeline;
}

export class CloudFoundryServerGroupArtifactSettings
  extends React.Component<ICloudFoundryCreateServerGroupArtifactSettingsProps>
  implements IWizardPageComponent<ICloudFoundryCreateServerGroupCommand> {
  public static get LABEL() {
    return 'Artifact';
  }

  private excludedArtifactTypePatterns = [
    ArtifactTypePatterns.KUBERNETES,
    ArtifactTypePatterns.DOCKER_IMAGE,
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
  ];

  private onExpectedArtifactSelected = (expectedArtifact: IExpectedArtifact): void => {
    this.props.formik.setFieldValue('applicationArtifact', { artifactId: expectedArtifact.id });
  };

  private onArtifactChanged = (artifact: IArtifact): void => {
    this.props.formik.setFieldValue('applicationArtifact', { artifact: artifact });
  };

  public validate(_values: ICloudFoundryCreateServerGroupCommand) {
    const { applicationArtifact } = this.props.formik.values;
    const errors = {} as any;
    if (
      !applicationArtifact ||
      !(
        (applicationArtifact.artifact && applicationArtifact.artifact.type && applicationArtifact.artifact.reference) ||
        applicationArtifact.artifactId
      )
    ) {
      errors.applicationArtifact = 'Application artifact information is required';
    }

    return errors;
  }

  public render() {
    const { formik, stage, pipeline } = this.props;
    const applicationArtifact = formik.values.applicationArtifact;
    return (
      <div className="form-group">
        <div className="col-md-11">
          <div className="StandardFieldLayout flex-container-h margin-between-lg">
            <div className="flex-grow">
              <StageArtifactSelector
                pipeline={pipeline}
                stage={stage}
                expectedArtifactId={applicationArtifact && applicationArtifact.artifactId}
                artifact={applicationArtifact && applicationArtifact.artifact}
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
  }
}
