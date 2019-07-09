import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { HelpField, HelpContentsRegistry } from '@spinnaker/core';

export interface IPolicyTypeSelectionModalProps {
  showCallback: () => void;
  typeSelectedCallback: (type: string) => void;
  warnOnMinMaxCapacity?: boolean;
}

// TODO: Replace with the custom icomoon version when it arrives
const StubbedInInfoIcon = () => (
  <div
    style={{
      border: '1px solid var(--color-accent)',
      borderRadius: '50%',
      fontWeight: 900,
      width: '25px',
      height: '24px',
      lineHeight: '24px',
      textAlign: 'center',
      margin: '7px',
      paddingLeft: '1px',
      fontFamily: 'Courier New',
    }}
  >
    i
  </div>
);

export function PolicyTypeSelectionModal(props: IPolicyTypeSelectionModalProps) {
  const [typeSelection, setTypeSelection] = React.useState(null);

  const selectType = (e: React.MouseEvent<HTMLElement>): void => {
    setTypeSelection(e.currentTarget.id);
  };

  const confirmTypeSelection = (): void => {
    props.typeSelectedCallback(typeSelection);
  };

  const stepClass = typeSelection === 'step' ? 'card active' : 'card';
  const targetClass = typeSelection === 'targetTracking' ? 'card active' : 'card';
  const customHelpKey = 'aws.scalingPolicy.additionalHelp';
  const hasCustomHelpMessage = !!HelpContentsRegistry.getHelpField(customHelpKey);
  return (
    <Modal show={true} onHide={props.showCallback}>
      <Modal.Header closeButton={true}>
        <h3>Select a policy type</h3>
      </Modal.Header>
      <Modal.Body>
        <div className="card-choices">
          <div className={targetClass} onClick={selectType} id="targetTracking">
            <h3>Target Tracking</h3>
            <div>Continuously adjusts the size of the ASG to keep a specified metric at the target value</div>
          </div>
          <div className={stepClass} onClick={selectType} id="step">
            <h3>Step</h3>
            <div>
              Rule-based scaling, with the ability to define different scaling amounts depending on the magnitude of the
              alarm breach
            </div>
          </div>
        </div>
        {hasCustomHelpMessage && (
          <div className="messageContainer previewMessage">
            <StubbedInInfoIcon />
            <div className="message">
              <HelpField id={customHelpKey} expand={true} />
            </div>
          </div>
        )}
        {props.warnOnMinMaxCapacity && (
          <div className="messageContainer warningMessage">
            <i className="fa icon-alert-triangle" />
            <div className="message">
              <p>
                This server group's <em>min</em> and <em>max</em> capacity are identical, so scaling policies will have{' '}
                <b>no effect.</b>
              </p>
              <p>
                Scaling policies work by adjusting the server group's <em>desired</em> capacity to a value between the
                min and max.
              </p>
            </div>
          </div>
        )}
      </Modal.Body>
      <Modal.Footer>
        <button className="btn btn-default" onClick={props.showCallback}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={!typeSelection} onClick={confirmTypeSelection}>
          Next
        </button>
      </Modal.Footer>
    </Modal>
  );
}
