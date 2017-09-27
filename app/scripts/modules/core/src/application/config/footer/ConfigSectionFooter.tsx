import * as React from 'react';
import { BindAll } from 'lodash-decorators';

export interface IConfigSectionFooterProps {
  isDirty: boolean;
  isValid: boolean;
  isSaving: boolean;
  saveError: boolean;
  onRevertClicked: () => void;
  onSaveClicked: () => void;
}

@BindAll()
export class ConfigSectionFooter extends React.Component<IConfigSectionFooterProps> {
  public render() {
    const { isValid, isDirty, isSaving, saveError, onRevertClicked, onSaveClicked } = this.props;

    if (!isDirty) {
      return (
        <div className="row footer">
          <div className="col-md-12 text-right">
            <span className="btn btn-link disabled"><span className="fa fa-check-circle-o"/> In sync with server</span>
          </div>
        </div>
      )
    }

    const saveButton = (
      <button className={`btn btn-primary ${isValid ? '' : 'disabled'}`} onClick={onSaveClicked}>
        <span>
          <span className="fa fa-check-circle-o"/> Save Changes
        </span>
      </button>
    );

    const savingButton = (
      <button className="btn btn-primary" disabled={true}>
        <span className="pulsing">
            <span className="fa fa-cog fa-spin"/> Saving&hellip;
          </span>
      </button>
    );

    return (
      <div className="row footer">
        <div className="col-md-3">
          <button className="btn btn-default" onClick={onRevertClicked} style={{visibility: 'visible'}}>
            <span className="glyphicon glyphicon-flash"/> Revert
          </button>
        </div>

        <div className="col-md-9 text-right">
          {isSaving ? savingButton : saveButton}
          {!!saveError && <div className="error-message">There was an error saving your changes. Please try again.</div>}
        </div>
      </div>
    );
  }
}
