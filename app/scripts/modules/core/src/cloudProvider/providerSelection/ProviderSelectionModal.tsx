import React from 'react';
import { Modal } from 'react-bootstrap';

import { CloudProviderRegistry } from '../CloudProviderRegistry';
import { ModalClose } from '../../modal';
import { IModalComponentProps, ReactModal } from '../../presentation';

export interface IProviderSelectionModalProps extends IModalComponentProps {
  providerOptions: string[];
}

export interface IProviderSelectionModalState {
  selectedProvider?: string;
}

export class ProviderSelectionModal extends React.Component<
  IProviderSelectionModalProps,
  IProviderSelectionModalState
> {
  constructor(props: IProviderSelectionModalProps) {
    super(props);
    this.state = {};
  }

  public static show(props: IProviderSelectionModalProps): Promise<string> {
    const modalProps = {};
    return ReactModal.show(ProviderSelectionModal, props, modalProps);
  }

  private setProvider(selectedProvider: string) {
    this.setState({ selectedProvider });
  }

  private cancel = () => {
    this.props.dismissModal();
  };

  private selectProvider = () => {
    this.props.closeModal(this.state.selectedProvider);
  };

  public render() {
    const { selectedProvider } = this.state;
    const { providerOptions } = this.props;

    return (
      <>
        <ModalClose dismiss={this.cancel} />
        <Modal.Header>
          <Modal.Title>Select Your Provider</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className="card-choices">
            {providerOptions.map((provider) => (
              <div
                className={`card vertical center middle ${selectedProvider === provider && 'active'}`}
                key={provider}
                onClick={() => this.setProvider(provider)}
              >
                {CloudProviderRegistry.hasValue(provider, 'logo.path') && (
                  <img
                    src={CloudProviderRegistry.getValue(provider, 'logo.path')}
                    alt={provider}
                    width="100%"
                    height="auto"
                  />
                )}
                {!CloudProviderRegistry.hasValue(provider, 'logo.path') && <h3 className="card-label">{provider}</h3>}
              </div>
            ))}
          </div>
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" onClick={this.cancel}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={this.selectProvider} disabled={!selectedProvider}>
            Next
          </button>
        </Modal.Footer>
      </>
    );
  }
}
