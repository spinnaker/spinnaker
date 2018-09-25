import * as React from 'react';
import { FormikErrors, FormikProps, FormikValues } from 'formik';

import { IWizardPageProps, wizardPage, Application } from '@spinnaker/core';

import { YamlEditor } from 'kubernetes/v2/manifest/yaml/YamlEditor';
import { IKubernetesManifestCommandData } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

export interface IServerGroupBasicSettingsProps extends IWizardPageProps {
  app: Application;
}

class ManifestEntryImpl extends React.Component<IServerGroupBasicSettingsProps> {
  public static LABEL = 'Manifest';

  constructor(props: IServerGroupBasicSettingsProps & IWizardPageProps & FormikProps<IKubernetesManifestCommandData>) {
    super(props);
  }

  public validate = (_values: FormikValues): FormikErrors<IKubernetesManifestCommandData> => {
    const errors = {} as FormikErrors<IKubernetesManifestCommandData>;
    return errors;
  };

  private handleChange = (manifests: any) => {
    const { formik } = this.props;
    if (!formik.values.command.manifests) {
      formik.values.command.manifests = [];
    }
    Object.assign(formik.values.command.manifests, Array.isArray(manifests) ? manifests : [manifests]);
  };

  public render() {
    const { formik } = this.props;
    const [first = null, ...rest] = formik.values.command.manifests || [];
    const manifest = rest && rest.length ? formik.values.command.manifests : first;
    return <YamlEditor value={manifest} onChange={this.handleChange} />;
  }
}

export const ManifestEntry = wizardPage<IServerGroupBasicSettingsProps>(ManifestEntryImpl);
