import * as React from 'react';

import { Option } from 'react-select';

import { FormikFormField, IArtifactAccount, IWizardPageProps, wizardPage } from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from 'cloudfoundry/serverGroup/configure//serverGroupConfigurationModel.cf';
import { ArtifactSelection } from 'cloudfoundry/serverGroup/configure/wizard/sections/artifactSettings/ArtifactSelection';
import { ArtifactTrigger } from 'cloudfoundry/serverGroup/configure/wizard/sections/artifactSettings/ArtifactTrigger';
import { CloudFoundryRadioButtonInput } from 'cloudfoundry/presentation/forms/inputs/CloudFoundryRadioButtonInput';
import { ArtifactPackage } from 'cloudfoundry/serverGroup/configure/wizard/sections/artifactSettings/ArtifactPackage';

export interface ICloudFoundryCreateServerGroupArtifactSettingsProps
  extends IWizardPageProps<ICloudFoundryCreateServerGroupCommand> {
  artifactAccounts: IArtifactAccount[];
  artifact?: any;
}

class ArtifactSettingsImpl extends React.Component<ICloudFoundryCreateServerGroupArtifactSettingsProps> {
  public static get LABEL() {
    return 'Artifact';
  }

  private artifactTypeUpdated = (type: string): void => {
    switch (type) {
      case 'package':
        this.props.formik.setFieldValue('artifact.account', '');
        this.props.formik.setFieldValue('artifact.clusterName', '');
        this.props.formik.setFieldValue('artifact.region', '');
        this.props.formik.setFieldValue('artifact.serverGroupName', '');
        break;
      case 'artifact':
        this.props.formik.setFieldValue('artifact.account', '');
        this.props.formik.setFieldValue('artifact.reference', '');
        break;
      case 'trigger':
        this.props.formik.setFieldValue('artifact.account', '');
        this.props.formik.setFieldValue('artifact.pattern', '');
        break;
    }
    this.props.formik.setFieldValue('artifact.type', type);
  };

  private getArtifactTypeOptions(): Array<Option<string>> {
    const { mode } = this.props.formik.values.viewState;
    if (mode === 'editPipeline' || mode === 'createPipeline') {
      return [
        { label: 'Artifact', value: 'artifact' },
        { label: 'Trigger', value: 'trigger' },
        { label: 'Package', value: 'package' },
      ];
    } else {
      return [{ label: 'Artifact', value: 'artifact' }, { label: 'Package', value: 'package' }];
    }
  }

  private getArtifactTypeInput(): JSX.Element {
    return (
      <div className="sp-margin-m-bottom">
        <FormikFormField
          name="artifact.type"
          label="Source Type"
          input={props => <CloudFoundryRadioButtonInput {...props} options={this.getArtifactTypeOptions()} />}
          onChange={this.artifactTypeUpdated}
        />
      </div>
    );
  }

  private getArtifactContentInput(): JSX.Element {
    switch (this.props.formik.values.artifact.type) {
      case 'package':
        return <ArtifactPackage formik={this.props.formik} />;
      case 'trigger':
        return <ArtifactTrigger artifactAccounts={this.props.artifactAccounts} />;
      default:
        return <ArtifactSelection artifactAccounts={this.props.artifactAccounts} />;
    }
  }

  public render(): JSX.Element {
    return (
      <div className="form-group">
        {this.getArtifactTypeInput()}
        {this.getArtifactContentInput()}
      </div>
    );
  }

  public validate(_values: ICloudFoundryCreateServerGroupArtifactSettingsProps) {
    return {};
  }
}

export const CloudFoundryServerGroupArtifactSettings = wizardPage(ArtifactSettingsImpl);
