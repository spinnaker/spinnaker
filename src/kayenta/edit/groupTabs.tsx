import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { ICanaryState } from '../reducers';
import { SELECT_GROUP } from '../actions/index';
import * as Creators from '../actions/creators';
import { Tabs, Tab } from '../layout/tabs';

export const UNGROUPED = '(ungrouped)';


interface IGroupTabsStateProps {
  groupList: string[];
  selectedGroup: string;
}

interface IGroupTabsDispatchProps {
  selectGroup: (event: any) => void,
  addGroup: () => void
}

/*
 * Configures an entire list of metrics.
 */
function GroupTabs({ groupList, selectedGroup, selectGroup, addGroup }: IGroupTabsStateProps & IGroupTabsDispatchProps) {
  const GroupTab = ({ group }: { group: string }) => (
    <Tab selected={selectedGroup === group}>
      <a data-group={group} onClick={selectGroup}>{group || '(all)'}</a>
    </Tab>
  );
  return (
    <section>
      <Tabs>
        <GroupTab group=""/>
        {groupList.map(group => <GroupTab key={group} group={group}/>)}
        <GroupTab group={UNGROUPED}/>
        <button className="passive float-right" onClick={addGroup}>Add Group</button>
      </Tabs>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IGroupTabsStateProps {
  return {
    groupList: state.selectedConfig.group.list,
    selectedGroup: state.selectedConfig.group.selected,
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IGroupTabsDispatchProps {
  return {
    selectGroup: (event: any) => {
      const name = event.target.dataset.group;
      dispatch({ type: SELECT_GROUP, name });
      event.preventDefault();
      event.stopPropagation();
    },
    addGroup: () => {
      dispatch(Creators.addGroup());
      event.preventDefault();
      event.stopPropagation();
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(GroupTabs);
