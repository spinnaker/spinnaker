import * as React from 'react';
import { FormikErrors } from 'formik';

import { IWizardPageProps, wizardPage, Application } from '@spinnaker/core';

import { YamlEditor } from 'kubernetes/v2/manifest/yaml/YamlEditor';
import { IKubernetesManifestCommandData } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

export interface IServerGroupBasicSettingsProps extends IWizardPageProps<IKubernetesManifestCommandData> {
  app: Application;
}

class ManifestEntryImpl extends React.Component<IServerGroupBasicSettingsProps> {
  public static LABEL = 'Manifest';

  public validate = (_values: IKubernetesManifestCommandData) => {
    return {} as FormikErrors<IKubernetesManifestCommandData>;
  };

  private handleChange = (manifests: any) => {
    const { values } = this.props.formik;
    if (!values.command.manifests) {
      values.command.manifests = [];
    }
    Object.assign(values.command.manifests, Array.isArray(manifests) ? manifests : [manifests]);
  };

  public render() {
    const { values } = this.props.formik;
    const [first = null, ...rest] = values.command.manifests || [];
    const manifest = rest && rest.length ? values.command.manifests : first;
    return <YamlEditor value={manifest} onChange={this.handleChange} />;
  }
}

export const ManifestEntry = wizardPage<IServerGroupBasicSettingsProps>(ManifestEntryImpl);
