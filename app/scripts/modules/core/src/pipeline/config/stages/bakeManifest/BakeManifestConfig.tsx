import * as React from 'react';
import { defaults } from 'lodash';

import { IStage } from 'core/domain';
import { FormikStageConfig, IStageConfigProps } from 'core/pipeline';
import { BakeManifestStageForm } from './BakeManifestStageForm';

export class BakeManifestConfig extends React.Component<IStageConfigProps> {
  private readonly stage: IStage;

  public constructor(props: IStageConfigProps) {
    super(props);

    // Intentionally initializing the stage config only once in the constructor
    // The stage config is then completely owned within FormikStageConfig's Formik state
    this.stage = props.stage;
  }

  public componentDidMount() {
    defaults(this.stage, {
      inputArtifacts: [],
      overrides: {},
    });
  }

  public render() {
    return (
      <FormikStageConfig
        {...this.props}
        stage={this.stage}
        onChange={this.props.updateStage}
        render={props => <BakeManifestStageForm {...props} updatePipeline={this.props.updatePipeline} />}
      />
    );
  }
}
