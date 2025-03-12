import * as Creators from 'kayenta/actions/creators';
import { CanarySettings } from 'kayenta/canary.settings';
import { DISABLE_EDIT_CONFIG, DisableableInput } from 'kayenta/layout/disableable';
import { ICanaryState } from 'kayenta/reducers';
import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';

interface IGroupNameStateProps {
  group: string;
  edit: string;
  editing: boolean;
}

interface IGroupNameDispatchProps {
  handleSubmit: (event: any) => void;
  handleUpdate: (event: any) => void;
}

export interface IGroupNameOwnProps {
  group: string;
  editing: boolean;
  onClick: (event: React.SyntheticEvent<HTMLAnchorElement>) => void;
  defaultGroup: string;
}

/*
 * Configures a group name.
 */
function GroupName({
  editing,
  edit,
  group,
  onClick,
  handleUpdate,
  handleSubmit,
  defaultGroup,
}: IGroupNameStateProps & IGroupNameDispatchProps & IGroupNameOwnProps) {
  if (editing) {
    return (
      <form onSubmit={handleSubmit} onBlur={handleSubmit} data-group={group} data-edit={edit}>
        <DisableableInput
          autoFocus={true}
          value={edit}
          onChange={handleUpdate}
          disabled={CanarySettings.disableConfigEdit}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </form>
    );
  } else {
    return (
      <a data-group={group} onClick={onClick}>
        {group || defaultGroup}
      </a>
    );
  }
}

function mapStateToProps(state: ICanaryState, ownProps: IGroupNameOwnProps): IGroupNameStateProps & IGroupNameOwnProps {
  return {
    edit: state.selectedConfig.group.edit,
    ...ownProps,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IGroupNameDispatchProps {
  return {
    handleUpdate: (event: any) => {
      dispatch(Creators.editGroupUpdate({ edit: event.target.value }));
    },
    handleSubmit: (event: any) => {
      dispatch(Creators.editGroupConfirm({ ...event.currentTarget.dataset }));
      event.stopPropagation();
      event.preventDefault();
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(GroupName);
