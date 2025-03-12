import React from 'react';
import { Modal } from 'react-bootstrap';
import { ArtifactService } from '../pipeline/config/triggers/artifacts/ArtifactService';
import { decodeUnicodeBase64 } from '../utils';

type IManifestYamlProps = {
  linkName: string;
  modalTitle: string;
} & ({ manifestText: string; manifestUri?: never } | { manifestText?: never; manifestUri: string });

export function ManifestYaml({ linkName, modalTitle, manifestText, manifestUri }: IManifestYamlProps) {
  const [modalVisible, setModalVisible] = React.useState<boolean>(false);
  const toggle = () => setModalVisible(!modalVisible);
  const [fetchedManifestText, setFetchedManifestText] = React.useState<string>('Loading...');
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (manifestUri) {
      ArtifactService.getArtifactByContentReference(manifestUri)
        .then((manifest) => setFetchedManifestText(decodeUnicodeBase64(manifest.reference)))
        .catch((e) => setError(`Error: ${typeof e !== 'string' ? e.data?.message ?? JSON.stringify(e) : e}`));
    }
  }, []);

  return (
    <>
      <a key="modal-link" onClick={toggle} className="clickable">
        {linkName}
      </a>
      <Modal key="modal" show={modalVisible} onHide={toggle}>
        <Modal.Header closeButton={true}>
          <h3>{modalTitle}</h3>
        </Modal.Header>
        <Modal.Body>
          {error ? (
            <div className="alert alert-warning">
              <p>{error}</p>
            </div>
          ) : null}
          <textarea
            readOnly={true}
            rows={15}
            className="code"
            value={manifestUri ? fetchedManifestText : manifestText}
          />
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
