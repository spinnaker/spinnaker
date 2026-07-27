import React from 'react';

import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import { robotToHuman } from '../../presentation/robotToHumanFilter/robotToHuman.filter';
import { logger } from '../../utils';

export interface IExecutionDetailsSectionNavProps {
  sections: string[];
}

export interface IExecutionDetailsSectionNavState {
  activeSection: string;
}

export class ExecutionDetailsSectionNavComponent extends React.Component<
  IExecutionDetailsSectionNavProps & IRouterInjectedProps,
  IExecutionDetailsSectionNavState
> {
  public constructor(props: IExecutionDetailsSectionNavProps & IRouterInjectedProps) {
    super(props);
    this.state = this.getState(props);
  }

  private getState(props: IExecutionDetailsSectionNavProps & IRouterInjectedProps): IExecutionDetailsSectionNavState {
    const activeSection = props.stateParams.details || props.sections[0];
    return { activeSection };
  }

  public componentWillReceiveProps(nextProps: IExecutionDetailsSectionNavProps & IRouterInjectedProps): void {
    this.setState(this.getState(nextProps));
  }

  public render() {
    return (
      <ul className="nav nav-pills">
        {this.props.sections.map((section) => (
          <Section
            key={section}
            section={section}
            active={this.state.activeSection === section}
            stateService={this.props.stateService}
          />
        ))}
      </ul>
    );
  }
}

const Section = (props: {
  section: string;
  active: boolean;
  stateService: IRouterInjectedProps['stateService'];
}): JSX.Element => {
  const clicked = () => {
    logger.log({ category: 'Pipeline', action: 'Execution details section selected', data: { label: props.section } });
    props.stateService.go('.', { details: props.section });
  };
  return (
    <li>
      <a className={`clickable ${props.active ? 'active' : ''}`} onClick={clicked}>
        {robotToHuman(props.section)}
      </a>
    </li>
  );
};

export const ExecutionDetailsSectionNav = withRouter(ExecutionDetailsSectionNavComponent);
