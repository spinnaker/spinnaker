import React from 'react';
import { Modal } from 'react-bootstrap';

export interface IManifestYamlProps {
  linkName: string;
  manifestText: string;
  modalTitle: string;
}

export function ManifestYaml(props: IManifestYamlProps) {
  const [modalVisible, setModalVisible] = React.useState<boolean>(false);
  const toggle = () => setModalVisible(!modalVisible);
  return (
    <>
      <a key="modal-link" onClick={toggle} className="clickable">
        {props.linkName}
      </a>
      <Modal key="modal" show={modalVisible} onHide={toggle}>
        <Modal.Header closeButton={true}>
          <h3>{props.modalTitle}</h3>
        </Modal.Header>
        <Modal.Body>
          <textarea readOnly={true} rows={15} className="code" value={props.manifestText} />
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-primary" onClick={toggle}>
            Close
          </button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
