import React from 'react';
import { Subscription } from 'rxjs';

import { robotToHuman } from '../../presentation/robotToHumanFilter/robotToHuman.filter';
import { ReactInjector } from '../../reactShims';
import { logger } from '../../utils';

export interface IExecutionDetailsSectionNavProps {
  sections: string[];
}

export interface IExecutionDetailsSectionNavState {
  activeSection: string;
}

export class ExecutionDetailsSectionNav extends React.Component<
  IExecutionDetailsSectionNavProps,
  IExecutionDetailsSectionNavState
> {
  private stateChangeSuccessSubscription: Subscription;

  public constructor(props: IExecutionDetailsSectionNavProps) {
    super(props);
    this.state = this.getState(props);
  }

  public componentDidMount(): void {
    this.stateChangeSuccessSubscription = ReactInjector.stateEvents.stateChangeSuccess.subscribe(() =>
      this.setState(this.getState(this.props)),
    );
  }

  private getState(props: IExecutionDetailsSectionNavProps): IExecutionDetailsSectionNavState {
    const { $stateParams } = ReactInjector;
    const activeSection = $stateParams.details || props.sections[0];
    return { activeSection };
  }

  public componentWillReceiveProps(nextProps: IExecutionDetailsSectionNavProps): void {
    this.setState(this.getState(nextProps));
  }

  public componentWillUnmount(): void {
    this.stateChangeSuccessSubscription.unsubscribe();
  }

  public render() {
    return (
      <ul className="nav nav-pills">
        {this.props.sections.map((section) => (
          <Section key={section} section={section} active={this.state.activeSection === section} />
        ))}
      </ul>
    );
  }
}

const Section = (props: { section: string; active: boolean }): JSX.Element => {
  const clicked = () => {
    logger.log({ category: 'Pipeline', action: 'Execution details section selected', data: { label: props.section } });
    ReactInjector.$state.go('.', { details: props.section });
  };
  return (
    <li>
      <a className={`clickable ${props.active ? 'active' : ''}`} onClick={clicked}>
        {robotToHuman(props.section)}
      </a>
    </li>
  );
};
