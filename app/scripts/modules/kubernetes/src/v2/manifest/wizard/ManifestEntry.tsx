import * as React from 'react';
import { FormikErrors, FormikProps, FormikValues } from 'formik';

import { IWizardPageProps, wizardPage, Application } from '@spinnaker/core';

import { YamlEditor } from 'kubernetes/v2/manifest/yaml/YamlEditor';
import { IKubernetesManifestCommandData } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

export interface IServerGroupBasicSettingsProps {
  app: Application;
}

class ManifestEntryImpl extends React.Component<
  IServerGroupBasicSettingsProps & IWizardPageProps & FormikProps<IKubernetesManifestCommandData>
> {
  public static LABEL = 'Manifest';

  constructor(props: IServerGroupBasicSettingsProps & IWizardPageProps & FormikProps<IKubernetesManifestCommandData>) {
    super(props);
  }

  public validate = (_values: FormikValues): FormikErrors<IKubernetesManifestCommandData> => {
    const errors = {} as FormikErrors<IKubernetesManifestCommandData>;
    return errors;
  };

  private handleChange = (manifests: any) => {
    if (!this.props.values.command.manifests) {
      this.props.values.command.manifests = [];
    }
    Object.assign(this.props.values.command.manifests, Array.isArray(manifests) ? manifests : [manifests]);
  };

  public render() {
    const { values } = this.props;
    const [first = null, ...rest] = values.command.manifests || [];
    const manifest = rest && rest.length ? values.command.manifests : first;
    return <YamlEditor value={manifest} onChange={this.handleChange} />;
  }
}

export const ManifestEntry = wizardPage<IServerGroupBasicSettingsProps>(ManifestEntryImpl);
