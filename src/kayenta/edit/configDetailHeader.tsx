import * as React from 'react';
import { connect } from 'react-redux';
import { UISref } from '@uirouter/react';

import { ICanaryState } from 'kayenta/reducers';
import { ICanaryConfig } from 'kayenta/domain';
import { mapStateToConfig } from 'kayenta/service/canaryConfig.service';
import FormattedDate from 'kayenta/layout/formattedDate';

import ConfigDetailActionButtons from './configDetailActionButtons';

interface IConfigDetailStateProps {
  selectedConfig: ICanaryConfig;
  editingDisabled: boolean;
}

const getOwnerAppLinks = (owners: string[]) => {
  if (owners.length === 1) {
    return (
      <UISref to="." params={{ application: owners[0] }}>
        <a>{owners[0]}</a>
      </UISref>
    );
  } else {
    // totally gross to read, but a somewhat-straightforward way of creating
    // a 'one, two, or three' sentence from this array of app names with some JSX in between
    const lastIndex = owners.length - 1;
    return owners.map((owner, index) => (
      <React.Fragment key={owner}>
        {index === lastIndex ? 'or ' : ''}
        <UISref to="." params={{ application: owner }}>
          <a>{owner}</a>
        </UISref>
        {index < lastIndex && owners.length === 2 ? ' ' : ''}
        {index < lastIndex && owners.length > 2 ? ', ' : ''}
      </React.Fragment>
    ));
  }
};

const EditingDisabledWarning = ({ owners }: { owners: string[] }) => {
  return (
    <div className="horizontal middle well-compact alert alert-warning config-detail-edit-warning">
      <i className="fa fa-exclamation-triangle sp-margin-m-right" />
      <span>
        <b>
          Editing is disabled because this config is owned by{' '}
          {owners.length > 1 ? `${owners.length} other applications` : 'another application'}.
        </b>{' '}
        To edit, view in {getOwnerAppLinks(owners)}
      </span>
    </div>
  );
};

/*
 * Config detail header layout.
 */
function ConfigDetailHeader({ selectedConfig, editingDisabled }: IConfigDetailStateProps) {
  return (
    <div className="vertical">
      <div className="horizontal config-detail-header">
        <div className="flex-3">
          <h1 className="heading-1 color-text-primary">{selectedConfig ? selectedConfig.name : ''}</h1>
        </div>
        <div className="flex-1">
          <h5 className="heading-5">
            <strong>Edited:</strong>{' '}
            <FormattedDate dateIso={selectedConfig ? selectedConfig.updatedTimestampIso : ''} />
          </h5>
        </div>
        <div className="flex-2">
          <ConfigDetailActionButtons />
        </div>
      </div>
      {selectedConfig && editingDisabled && <EditingDisabledWarning owners={selectedConfig.applications} />}
    </div>
  );
}

function mapStateToProps(state: ICanaryState): IConfigDetailStateProps {
  return {
    selectedConfig: mapStateToConfig(state),
    editingDisabled: state.app.disableConfigEdit,
  };
}

export default connect(mapStateToProps)(ConfigDetailHeader);
