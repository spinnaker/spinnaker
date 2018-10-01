import * as React from 'react';
import { FormikErrors } from 'formik';
import { dump } from 'js-yaml';

import { IWizardPageProps, wizardPage, Application } from '@spinnaker/core';

import { YamlEditor } from 'kubernetes/v2/manifest/yaml/YamlEditor';
import { IKubernetesManifestCommandData } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

export interface IManifestBasicSettingsProps extends IWizardPageProps<IKubernetesManifestCommandData> {
  app: Application;
}

export interface IManifestBasicSettingsState {
  rawManifest: string;
}

class ManifestEntryImpl extends React.Component<IManifestBasicSettingsProps, IManifestBasicSettingsState> {
  public static LABEL = 'Manifest';

  constructor(props: IManifestBasicSettingsProps) {
    super(props);

    const { values } = this.props.formik;
    const [first = null, ...rest] = values.command.manifests || [];
    const manifest = rest && rest.length ? values.command.manifests : first;
    try {
      this.state = {
        rawManifest: manifest ? dump(manifest) : null,
      };
    } catch (e) {
      this.state = { rawManifest: null };
    }
  }

  public validate = (_values: IKubernetesManifestCommandData) => {
    return {} as FormikErrors<IKubernetesManifestCommandData>;
  };

  private handleChange = (rawManifest: string, manifest: any) => {
    const { values } = this.props.formik;
    if (!values.command.manifests) {
      values.command.manifests = [];
    }
    if (manifest) {
      values.command.manifests = Array.isArray(manifest) ? manifest : [manifest];
    }
    this.setState({ rawManifest });
  };

  public render() {
    return <YamlEditor value={this.state.rawManifest} onChange={this.handleChange} />;
  }
}

export const ManifestEntry = wizardPage(ManifestEntryImpl);
