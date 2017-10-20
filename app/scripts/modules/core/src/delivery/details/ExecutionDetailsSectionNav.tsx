import * as React from 'react';
import * as ReactGA from 'react-ga';
import { UISref, UISrefActive } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';

import { robotToHuman } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';

export interface IExecutionDetailsSectionNavProps {
  sections: string[];
}

@UIRouterContext
export class ExecutionDetailsSectionNav extends React.Component<IExecutionDetailsSectionNavProps> {
  public render() {
    return (
      <ul className="nav nav-pills">
        {this.props.sections.map((section) => <Section key={section} section={section}/>)}
      </ul>
    );
  }
}

const Section = (props: { section: string }): JSX.Element => {
  const clicked = () => {
    ReactGA.event({category: 'Pipeline', action: 'Execution details section selected', label: props.section});
  }
  return (
    <li>
      <UISrefActive class="active">
        <UISref to=".execution" params={{details: props.section}}>
          <a onClick={clicked}>{robotToHuman(props.section)}</a>
        </UISref>
      </UISrefActive>
    </li>
  );
}
