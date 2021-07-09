import { module } from 'angular';
import { chain } from 'lodash';
import React from 'react';
import { Async, AutocompleteResult, Option } from 'react-select';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { IGceImage } from '../image';

interface IImageSelectProps {
  availableImages: IGceImage[];
  selectedImage: string;
  selectImage: (image: string, target?: any) => void;
  target?: any;
}

export class ImageSelect extends React.Component<IImageSelectProps> {
  private loadOptions = (inputValue: string): Promise<AutocompleteResult<string>> => {
    return new Promise((resolve) => {
      if (!inputValue || inputValue.length < 3) {
        resolve({
          options: [],
          complete: false,
        });
      } else {
        const filteredOptions = chain(this.props.availableImages)
          .filter((i) => i.imageName.toLowerCase().includes(inputValue))
          .take(20)
          .map((i) => ({ value: i.imageName, label: i.imageName }))
          .value();
        resolve({
          options: filteredOptions,
          complete: false,
        });
      }
    });
  };

  public render() {
    return (
      <Async
        cache={null}
        clearable={false}
        ignoreAccents={false}
        loadOptions={this.loadOptions}
        onChange={(selected: Option<string>) => this.props.selectImage(selected.value, this.props.target)}
        placeholder="Type at least 3 characters to search for an image..."
        searchPromptText="Type at least 3 characters to search for an image..."
        value={{ value: this.props.selectedImage, label: this.props.selectedImage }}
      />
    );
  }
}

export const GCE_IMAGE_SELECT = 'spinnaker.gce.imageSelect';
module(GCE_IMAGE_SELECT, []).component(
  'gceImageSelect',
  react2angular(withErrorBoundary(ImageSelect, 'gceImageSelect'), [
    'availableImages',
    'selectedImage',
    'selectImage',
    'target',
  ]),
);
