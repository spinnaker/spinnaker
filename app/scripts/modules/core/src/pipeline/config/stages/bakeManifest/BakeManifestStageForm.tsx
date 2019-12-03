import React from 'react';

import { IPipeline } from 'core/domain';
import { IFormikStageConfigInjectedProps, StageConfigField } from 'core/pipeline';
import { BakeKustomizeConfigForm } from './kustomize/BakeKustomizeConfigForm';
import { BakeHelmConfigForm } from './helm/BakeHelmConfigForm';
import { ReactSelectInput } from 'core/presentation';
import { SETTINGS } from 'core/config/settings';

interface IBakeManifestStageFormProps {
  updatePipeline: (pipeline: IPipeline) => void;
}

export class BakeManifestStageForm extends React.Component<
  IBakeManifestStageFormProps & IFormikStageConfigInjectedProps
> {
  public HELM_RENDERER = 'HELM2';
  public KUSTOMIZE_RENDERER = 'KUSTOMIZE';

  private templateRenderers = (): string[] => {
    const renderers = [this.HELM_RENDERER];
    if (SETTINGS.feature.kustomizeEnabled) {
      renderers.push(this.KUSTOMIZE_RENDERER);
    }
    return renderers;
  };

  private shouldRenderHelm(): boolean {
    const stage = this.props.formik.values;
    return stage.templateRenderer === this.HELM_RENDERER;
  }

  public render() {
    const stage = this.props.formik.values;
    return (
      <div className="form-horizontal clearfix">
        <div className="container-fluid form-horizontal">
          <h4>Template Renderer</h4>
          <StageConfigField
            fieldColumns={3}
            label={'Render Engine'}
            helpKey={'pipeline.config.bake.manifest.templateRenderer'}
          >
            <ReactSelectInput
              clearable={false}
              onChange={(o: React.ChangeEvent<HTMLSelectElement>) => {
                this.props.formik.setFieldValue('templateRenderer', o.target.value);
              }}
              value={stage.templateRenderer}
              stringOptions={this.templateRenderers()}
            />
          </StageConfigField>
          {stage.templateRenderer === this.KUSTOMIZE_RENDERER && (
            <BakeKustomizeConfigForm
              pipeline={this.props.pipeline}
              application={this.props.application}
              formik={this.props.formik}
              updatePipeline={this.props.updatePipeline}
            />
          )}
          {this.shouldRenderHelm() && (
            <BakeHelmConfigForm
              pipeline={this.props.pipeline}
              application={this.props.application}
              formik={this.props.formik}
              updatePipeline={this.props.updatePipeline}
            />
          )}
        </div>
      </div>
    );
  }
}
