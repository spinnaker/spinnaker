import React from 'react';

import { Spinner } from '../../../widgets/spinners/Spinner';

export interface IConfigSectionFooterProps {
  isDirty: boolean;
  isValid: boolean;
  isSaving: boolean;
  saveError: boolean;
  onRevertClicked: () => void;
  onSaveClicked: () => void;
}

export class ConfigSectionFooter extends React.Component<IConfigSectionFooterProps> {
  public render() {
    const { isValid, isDirty, isSaving, saveError, onRevertClicked, onSaveClicked } = this.props;

    if (!isDirty) {
      return (
        <div className="row footer">
          <div className="col-md-12 text-right">
            <span className="btn btn-link disabled">
              <span className="far fa-check-circle" /> In sync with server
            </span>
          </div>
        </div>
      );
    }

    const saveButton = (
      <button className={`btn btn-primary ${isValid ? '' : 'disabled'}`} onClick={onSaveClicked}>
        <span>
          <span className="far fa-check-circle" /> Save Changes
        </span>
      </button>
    );

    const savingButton = (
      <button className="btn btn-primary" disabled={true}>
        <span className="pulsing">
          <Spinner size="nano" /> Saving&hellip;
        </span>
      </button>
    );

    return (
      <div className="row footer">
        <div className="col-md-3">
          <button className="btn btn-default" onClick={onRevertClicked} style={{ visibility: 'visible' }}>
            <span className="glyphicon glyphicon-flash" /> Revert
          </button>
        </div>

        <div className="col-md-9 text-right">
          {isSaving ? savingButton : saveButton}
          {!!saveError && (
            <div className="error-message">There was an error saving your changes. Please try again.</div>
          )}
        </div>
      </div>
    );
  }
}
