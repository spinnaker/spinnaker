import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { dump } from 'js-yaml';
import { cloneDeep } from 'lodash';
import { IManifest } from '@spinnaker/core';

export interface IManifestYamlProps {
  manifest: IManifest;
  linkName: string;
}

export interface IManifestYamlState {
  modalVisible: boolean;
  manifestText: string;
}

export class ManifestYaml extends React.Component<IManifestYamlProps, IManifestYamlState> {
  constructor(props: IManifestYamlProps) {
    super(props);
    this.state = {
      modalVisible: false,
      manifestText: '',
    };
    this.toggle = this.toggle.bind(this);
  }

  private toggle() {
    const newState = {
      modalVisible: !this.state.modalVisible,
      manifestText: this.state.manifestText,
    };
    if (newState.modalVisible && newState.manifestText === '') {
      newState.manifestText = dump(cloneDeep(this.props.manifest.manifest));
    }
    this.setState(newState);
  }

  public render() {
    return [
      <a key="modal-link" onClick={this.toggle} className="clickable">
        {this.props.linkName}
      </a>,
      <Modal key="modal" show={this.state.modalVisible} onHide={this.toggle}>
        <Modal.Header closeButton={true}>
          <h3>{this.props.manifest.manifest.metadata.name}</h3>
        </Modal.Header>
        <Modal.Body>
          <textarea readOnly={true} rows={15} className="code" value={this.state.manifestText} />
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-primary" onClick={this.toggle}>
            Close
          </button>
        </Modal.Footer>
      </Modal>,
    ];
  }
}
