import { FormikProps } from 'formik';
import { dump } from 'js-yaml';
import React from 'react';

import { Application, YamlEditor } from '@spinnaker/core';

import { IKubernetesManifestCommandData } from '../manifestCommandBuilder.service';

export interface IManifestBasicSettingsProps {
  app: Application;
  formik: FormikProps<IKubernetesManifestCommandData>;
}

export interface IManifestBasicSettingsState {
  rawManifest: string;
}

export class ManifestEntry extends React.Component<IManifestBasicSettingsProps, IManifestBasicSettingsState> {
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

  private handleChange = (rawManifest: string, manifest: any) => {
    this.props.formik.setFieldValue(
      'command.manifests',
      [].concat(manifest).filter((x) => !!x),
    );
    this.setState({ rawManifest });
  };

  public render() {
    return <YamlEditor value={this.state.rawManifest} onChange={this.handleChange} />;
  }
}
