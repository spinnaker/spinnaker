import 'ace-builds/src-noconflict/mode-yaml';
import 'ace-builds/src-noconflict/theme-textmate';
import { dump as dumpYaml } from 'js-yaml';
import React from 'react';
import AceEditor from 'react-ace';
import { Modal } from 'react-bootstrap';
import { ArtifactService } from '../pipeline/config/triggers/artifacts/ArtifactService';
import { decodeUnicodeBase64 } from '../utils';

type IManifestYamlProps = {
  linkName: string;
  modalTitle: string;
} & ({ manifestText: string; manifestUri?: never } | { manifestText?: never; manifestUri: string });

// Stored entity-store references round-trip the manifest as JSON; re-render it as YAML for
// readability. Text that isn't JSON (e.g. a baked manifest, which is already YAML) is left as-is.
function formatAsYaml(text: string): string {
  try {
    return dumpYaml(JSON.parse(text));
  } catch {
    return text;
  }
}

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

  const rawText = manifestUri ? fetchedManifestText : manifestText;
  const yamlText = React.useMemo(() => formatAsYaml(rawText), [rawText]);

  return (
    <>
      <a key="modal-link" onClick={toggle} className="clickable">
        {linkName}
      </a>
      <Modal key="modal" show={modalVisible} onHide={toggle} bsSize="large">
        <Modal.Header closeButton={true}>
          <h3>{modalTitle}</h3>
        </Modal.Header>
        <Modal.Body>
          {error ? (
            <div className="alert alert-warning">
              <p>{error}</p>
            </div>
          ) : (
            <AceEditor
              mode="yaml"
              theme="textmate"
              name="manifest-yaml"
              value={yamlText}
              readOnly={true}
              fontSize={12}
              showGutter={true}
              showPrintMargin={false}
              highlightActiveLine={false}
              minLines={15}
              maxLines={100}
              width="100%"
              setOptions={{ useWorker: false, showLineNumbers: true, tabSize: 2 }}
              editorProps={{ $blockScrolling: Infinity }}
            />
          )}
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
