import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { ICanaryState } from '../reducers';
import { ADD_GROUP, SELECT_GROUP } from '../actions/index';

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
  return (
    <section>
      <ul className="nav nav-tabs">
        <li className={selectedGroup ? '' : 'active'}>
          <a data-group="" onClick={selectGroup}>(all)</a>
        </li>
        {groupList.map(group => (
          <li className={group === selectedGroup ? 'active' : ''} key={group}>
            <a data-group={group} onClick={selectGroup}>{group}</a>
          </li>
        ))}
        <li className={selectedGroup === UNGROUPED ? 'active' : ''}>
          <a data-group={UNGROUPED} onClick={selectGroup}>(ungrouped)</a>
        </li>
        <button className="float-right" onClick={addGroup}>Add Group</button>
      </ul>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IGroupTabsStateProps {
  return {
    groupList: state.groupList,
    selectedGroup: state.selectedGroup
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
      dispatch({ type: ADD_GROUP });
      event.preventDefault();
      event.stopPropagation();
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(GroupTabs);
