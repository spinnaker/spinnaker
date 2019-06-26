import * as React from 'react';
import { defaults } from 'lodash';
import { Observable, Subject } from 'rxjs';

import { FormikStageConfig, IStage, IStageConfigProps } from '@spinnaker/core';
import { KubernetesManifestCommandBuilder } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';
import { PatchManifestStageForm } from './PatchManifestStageForm';

export class PatchManifestStageConfig extends React.Component<IStageConfigProps> {
  private readonly stage: IStage;
  private destroy$ = new Subject();

  public constructor(props: IStageConfigProps) {
    super(props);

    // Intentionally initializing the stage config only once in the constructor
    // The stage config is then completely owned within FormikStageConfig's Formik state
    this.stage = props.stage;
  }

  public componentDidMount(): void {
    Observable.fromPromise(
      KubernetesManifestCommandBuilder.buildNewManifestCommand(
        this.props.application,
        this.stage.patchBody,
        this.stage.moniker,
      ),
    )
      .takeUntil(this.destroy$)
      .subscribe(builtCommand => {
        if (this.stage.isNew) {
          defaults(this.stage, {
            account: builtCommand.command.account,
            manifestArtifactAccount: 'embedded-artifact',
            patchBody: builtCommand.command.manifest,
            source: 'text',
            options: {
              record: true,
              strategy: 'strategic',
            },
            location: '',
            cloudProvider: 'kubernetes',
          });
        }
      });
  }

  public render() {
    return (
      <FormikStageConfig
        {...this.props}
        stage={this.stage}
        onChange={this.props.updateStage}
        render={props => <PatchManifestStageForm {...props} updatePipeline={this.props.updatePipeline} />}
      />
    );
  }
}
