import React from 'react';

import { MapEditor } from '@spinnaker/core';

import { AzureWizardPage } from './common';
import Utility from '../../../../utility';

export class ServerGroupTags extends AzureWizardPage {
  public validate(values: any): { [key: string]: any } {
    const tagResult = Utility.checkTags(values.instanceTags);
    return tagResult.isValid ? {} : { instanceTags: tagResult.errorMessage };
  }

  private tagsChanged = (instanceTags: any) => {
    this.props.formik.values.instanceTags = instanceTags;
    this.props.formik.setFieldValue('instanceTags', instanceTags);
  };

  public render() {
    const tagResult = Utility.checkTags(this.props.formik.values.instanceTags);
    return (
      <div className="container-fluid form-horizontal">
        {!tagResult.isValid && <div className="col-md-12 text-danger">{tagResult.errorMessage}</div>}
        <div className="form-group">
          <div className="col-md-4 sm-label-left">
            <b>Custom Tags</b>
          </div>
          <div className="col-md-12">
            <MapEditor
              addButtonLabel="Add New Tags"
              allowEmpty={true}
              model={this.props.formik.values.instanceTags || {}}
              onChange={this.tagsChanged}
            />
          </div>
        </div>
      </div>
    );
  }
}
