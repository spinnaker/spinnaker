import * as React from 'react';
import { connect } from 'react-redux';
import { flatMap, uniq } from 'lodash';

import GroupWeight from './groupWeight';
import { ICanaryState } from '../reducers/index';
import { mapStateToConfig } from '../service/canaryConfig.service';
import FormList from '../layout/formList';

interface IGroupWeightsStateProps {
  groups: string[];
}

/*
* Component for rendering list of group weight configurers.
*/
function GroupWeights({ groups }: IGroupWeightsStateProps) {
  const hasGroups = groups.length > 0;
  return (
    <section>
      <FormList>
        {hasGroups
          ? groups.map(group => <GroupWeight key={group} group={group}/>)
          : <p key="no-groups">You have not configured any grouped metrics.</p>
        }
      </FormList>
    </section>
  );
}

function mapStateToProps(state: ICanaryState): IGroupWeightsStateProps {
  const config = mapStateToConfig(state);
  const metrics = config ? config.metrics : [] ;
  const groups = uniq(flatMap(metrics, metric => metric.groups || []));
  return {
    groups,
  };
}

export default connect(mapStateToProps)(GroupWeights);
