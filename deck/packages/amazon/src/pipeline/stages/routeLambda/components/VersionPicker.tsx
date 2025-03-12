// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import React from 'react';

import type { IVersionConstant } from '../constants';
import { VersionList } from '../constants';

export interface IVersionPickerProps {
  value: string;
  showingDetails: boolean;
}

export interface IVersionPickerState {
  value: string;
  label: string;
  description: string;
}

export class VersionPicker extends React.Component<IVersionPickerProps, IVersionPickerState> {
  constructor(props: IVersionPickerProps) {
    super(props);

    // In here we will link the 'value' to an actual version id - when this is passed to Orca it will have the ID

    const { value } = this.props;
    const versionDetails = VersionList.filter((v: IVersionConstant) => v.value === value)[0];
    this.state = {
      label: versionDetails.label,
      value: versionDetails.value,
      description: versionDetails.description,
    };
  }

  public render() {
    return (
      <div>
        <b> {this.state.label} </b> <br />
        <small> {this.state.description} </small>
      </div>
    );
  }
}
