import classNames from 'classnames';
import * as Creators from 'kayenta/actions/creators';
import { CanarySettings } from 'kayenta/canary.settings';
import { DISABLE_EDIT_CONFIG, DisableableButton } from 'kayenta/layout/disableable';
import { Tab, Tabs } from 'kayenta/layout/tabs';
import { ICanaryState } from 'kayenta/reducers';
import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';

import { noop } from '@spinnaker/core';

import GroupName from './groupName';

export const ALL = 'all';

interface IGroupTabsStateProps {
  groupList: string[];
  selectedGroup: string;
  editing: boolean;
  disableConfigEdit: boolean;
}

interface IGroupTabsDispatchProps {
  selectGroup: (event: any) => void;
  addGroup: (event: any) => void;
  editGroupBegin: (event: any) => void;
}

/*
 * Configures an entire list of metrics.
 */
function GroupTabs({
  groupList,
  selectedGroup,
  selectGroup,
  addGroup,
  editing,
  editGroupBegin,
  disableConfigEdit,
}: IGroupTabsStateProps & IGroupTabsDispatchProps) {
  const GroupTab = ({ group, editable = false }: { group: string; editable?: boolean }) => {
    const selected = selectedGroup === group;
    return (
      <Tab selected={selected}>
        <GroupName group={group} editing={selected && editing} onClick={selectGroup} defaultGroup={ALL} />
        {selected && editable && !editing && (
          <i
            data-group={group}
            onClick={disableConfigEdit || CanarySettings.disableConfigEdit ? noop : editGroupBegin}
            className={classNames('fas', 'fa-pencil-alt', {
              disabled: disableConfigEdit || CanarySettings.disableConfigEdit,
            })}
          />
        )}
      </Tab>
    );
  };
  return (
    <section className="group-tabs">
      <Tabs style={{ marginBottom: '0' }}>
        <GroupTab group="" />
        {groupList.map((group) => (
          <GroupTab key={group} group={group} editable={true} />
        ))}
        <DisableableButton
          className="passive float-right"
          onClick={addGroup}
          disabled={CanarySettings.disableConfigEdit}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        >
          Add Group
        </DisableableButton>
      </Tabs>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IGroupTabsStateProps {
  return {
    groupList: state.selectedConfig.group.list,
    selectedGroup: state.selectedConfig.group.selected,
    editing: !!state.selectedConfig.group.edit || state.selectedConfig.group.edit === '',
    disableConfigEdit: state.app.disableConfigEdit || CanarySettings.disableConfigEdit,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IGroupTabsDispatchProps {
  return {
    selectGroup: (event: any) => {
      dispatch(Creators.selectGroup({ name: event.target.dataset.group }));
      event.preventDefault();
      event.stopPropagation();
    },
    addGroup: (event: any) => {
      dispatch(Creators.addGroup());
      event.preventDefault();
      event.stopPropagation();
    },
    editGroupBegin: (event: any) => {
      dispatch(Creators.editGroupBegin({ group: event.target.dataset.group }));
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(GroupTabs);
