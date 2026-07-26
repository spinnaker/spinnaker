import React from 'react';

import { AzureWizardPage } from './common';

export class ServerGroupImageSettings extends AzureWizardPage {
  public validate(values: any): { [key: string]: any } {
    if (values.viewState?.disableImageSelection) {
      return {};
    }
    if (values.image?.isCustom) {
      const errors: { [key: string]: any } = {};
      if (!values.image.imageName) {
        errors.imageName = 'Image name required.';
      }
      if (!values.image.ostype) {
        errors.ostype = 'OS type required.';
      }
      if (!values.image.uri) {
        errors.uri = 'URI required.';
      }
      return errors;
    }
    if (!values.selectedImage) {
      return { selectedImage: 'Image required.' };
    }
    return {};
  }

  private isRegionalImage(image: any, region: string): boolean {
    if (!region) {
      return true;
    }
    if (image.amis) {
      return !!image.amis[region];
    }
    return image.region === region || image.region == null;
  }

  private imageChanged = (imageName: string) => {
    const images = this.props.formik.values.backingData?.filtered?.images || this.props.formik.values.images || [];
    const image = images.find((candidate: any) => candidate.imageName === imageName) || null;
    this.props.formik.values.imageName = imageName;
    this.props.formik.values.selectedImage = image;
    this.props.formik.setFieldValue('imageName', imageName);
    this.props.formik.setFieldValue('selectedImage', image);
  };

  private customImageChanged = (isCustom: boolean) => {
    const image = isCustom
      ? { ...(this.props.formik.values.image || {}), isCustom, region: this.props.formik.values.region }
      : { isCustom };
    this.props.formik.values.image = image;
    this.props.formik.setFieldValue('image', image);
  };

  private customImageFieldChanged = (field: string, value: string) => {
    const image = {
      ...(this.props.formik.values.image || {}),
      isCustom: true,
      region: this.props.formik.values.region,
      [field]: value,
    };
    this.props.formik.values.image = image;
    this.props.formik.setFieldValue('image', image);
  };

  public render() {
    const { values } = this.props.formik;
    const imageOptions = values.backingData?.filtered?.images || values.images || [];
    const images = values.region
      ? imageOptions.filter((image: any) => this.isRegionalImage(image, values.region))
      : imageOptions;
    const image = values.image || {};

    if (values.viewState?.disableImageSelection) {
      return <div className="container-fluid form-horizontal">Image selection is disabled for this command.</div>;
    }

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Use custom image?</div>
          <div className="col-md-7">
            <input
              checked={!!image.isCustom}
              onChange={(event) => this.customImageChanged(event.target.checked)}
              type="checkbox"
            />
          </div>
        </div>
        {image.isCustom && (
          <>
            <div className="form-group">
              <div className="col-md-3 sm-label-right">Region</div>
              <div className="col-md-7">{values.region}</div>
            </div>
            <div className="form-group">
              <div className="col-md-3 sm-label-right">Image Name</div>
              <div className="col-md-7">
                <input
                  className="form-control input-sm"
                  onChange={(event) => this.customImageFieldChanged('imageName', event.target.value)}
                  value={image.imageName || ''}
                />
              </div>
            </div>
            <div className="form-group">
              <div className="col-md-3 sm-label-right">OS Type</div>
              <div className="col-md-7">
                <input
                  className="form-control input-sm"
                  onChange={(event) => this.customImageFieldChanged('ostype', event.target.value)}
                  value={image.ostype || ''}
                />
              </div>
            </div>
            <div className="form-group">
              <div className="col-md-3 sm-label-right">URI</div>
              <div className="col-md-7">
                <input
                  className="form-control input-sm"
                  onChange={(event) => this.customImageFieldChanged('uri', event.target.value)}
                  value={image.uri || ''}
                />
              </div>
            </div>
          </>
        )}
        {!image.isCustom && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Image</div>
            <div className="col-md-7">
              <select
                className="form-control input-sm"
                onChange={(event) => this.imageChanged(event.target.value)}
                value={values.imageName || values.selectedImage?.imageName || ''}
              >
                <option value="">Select...</option>
                {images.map((image: any) => (
                  <option key={image.imageName} value={image.imageName}>
                    {image.imageName}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}
      </div>
    );
  }
}
