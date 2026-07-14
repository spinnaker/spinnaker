import React from 'react';

import { ArtifactTypePatterns, excludeAllTypesExcept, Markdown, StageArtifactSelectorDelegate } from '@spinnaker/core';

import { preservePersistedReference, validateGceServerGroupCommand } from '../GceServerGroupWizard.helpers';
import type { IGceServerGroupCommand, IGceServerGroupCommandValidationErrors } from '../GceServerGroupWizard.types';
import { GceServerGroupWizardPage } from '../GceServerGroupWizardPage';

interface IImageOption {
  label: string;
  unavailable: boolean;
  value: string;
}

const IMAGE_SOURCES = [
  { label: 'Artifact', value: 'artifact' },
  { label: 'Prior Stage', value: 'priorStage' },
];

export class ServerGroupImageSettings extends GceServerGroupWizardPage {
  public validate(values: IGceServerGroupCommand): IGceServerGroupCommandValidationErrors {
    const image = validateGceServerGroupCommand(values).image;
    return image ? { image } : {};
  }

  private imageArtifactEdited = (artifact: any): void => {
    this.props.formik.setFieldValue('imageArtifactId', null);
    this.props.formik.setFieldValue('imageArtifact', artifact);
  };

  private expectedImageArtifactSelected = (expectedArtifact: any): void => {
    this.props.formik.setFieldValue('imageArtifactId', expectedArtifact.id);
    this.props.formik.setFieldValue('imageArtifact', null);
  };

  private renderImageSource(): React.ReactNode {
    const { values } = this.props.formik;
    if (!values.viewState.showImageSourceSelector) {
      return null;
    }
    const sourceControl = values.viewState.imageSourceText ? (
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Image Source</div>
        <div className="col-md-7">
          <Markdown tag="span" message={values.viewState.imageSourceText} />
        </div>
      </div>
    ) : (
      <div className="form-group">
        <label className="col-md-3 sm-label-right" htmlFor="gce-server-group-image-source">
          Image Source
        </label>
        <div className="col-md-7">
          <select
            aria-label="Image source"
            className="form-control input-sm"
            id="gce-server-group-image-source"
            onChange={(event) => this.props.formik.setFieldValue('imageSource', event.target.value)}
            value={values.imageSource || ''}
          >
            {preservePersistedReference(
              IMAGE_SOURCES,
              values.imageSource,
              (source) => source.value,
              (value) => ({ label: value, value }),
            ).map(({ unresolved, value }) => (
              <option disabled={unresolved} key={value.value} value={value.value}>
                {unresolved ? `${value.label} (unavailable)` : value.label}
              </option>
            ))}
          </select>
        </div>
      </div>
    );

    return (
      <>
        {sourceControl}
        {values.imageSource === 'artifact' && (
          <StageArtifactSelectorDelegate
            artifact={values.imageArtifact}
            excludedArtifactTypePatterns={excludeAllTypesExcept(ArtifactTypePatterns.CUSTOM_OBJECT)}
            expectedArtifactId={values.imageArtifactId}
            fieldColumns={7}
            helpKey="gce.image.artifact"
            label="Expected Artifact"
            onArtifactEdited={this.imageArtifactEdited}
            onExpectedArtifactSelected={this.expectedImageArtifactSelected}
            pipeline={values.viewState.pipeline}
            stage={values.viewState.stage}
          />
        )}
      </>
    );
  }

  private renderImageSelect(): React.ReactNode {
    const { values } = this.props.formik;
    if (values.viewState.disableImageSelection) {
      return <div>Image selection is disabled for this command.</div>;
    }

    const availableImages = values.backingData?.filtered?.images || values.backingData?.allImages || [];
    const images = imageOptions(availableImages, values.image);
    const error = this.validate(values).image;
    return (
      <div className="form-group">
        <label className="col-md-3 sm-label-right" htmlFor="gce-server-group-image">
          Image
        </label>
        <div className="col-md-7">
          <select
            aria-describedby={error ? 'gce-server-group-image-error' : undefined}
            aria-invalid={Boolean(error)}
            aria-label="Image"
            className="form-control input-sm"
            id="gce-server-group-image"
            onChange={(event) => this.props.formik.setFieldValue('image', event.target.value)}
            required={true}
            value={values.image || ''}
          >
            <option value="">Select...</option>
            {images.map((image) => (
              <option disabled={image.unavailable} key={image.value} value={image.value}>
                {image.label}
              </option>
            ))}
          </select>
          {error && (
            <span className="help-block" id="gce-server-group-image-error" role="alert">
              {error}
            </span>
          )}
        </div>
      </div>
    );
  }

  public render(): JSX.Element {
    return (
      <div className="container-fluid form-horizontal">
        {this.renderImageSource()}
        {this.renderImageSelect()}
      </div>
    );
  }
}

function imageOptions(rawImages: readonly any[], persistedImage: string | null | undefined): IImageOption[] {
  const images = rawImages.map((image) => (typeof image === 'string' ? image : image.imageName)).filter(Boolean);
  return preservePersistedReference(
    images,
    persistedImage,
    (image) => image,
    (image) => image,
  ).map(({ unresolved, value }) => ({
    label: unresolved ? `${value} (unavailable)` : value,
    unavailable: unresolved,
    value,
  }));
}
