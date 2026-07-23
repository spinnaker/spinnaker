import { isEqual } from 'lodash';
import React from 'react';

import type { IDeckRuntimeServicesInjectedProps } from '../../../../bootstrap/DeckRuntimeContext';
import { withDeckRuntimeServices } from '../../../../bootstrap/DeckRuntimeContext';
import { ExecutionDetailsSectionNav } from '../../../details';
import type { IExecutionDetailsProps, IExecutionDetailsState } from '../../../../domain';
import type { IRouterInjectedProps } from '../../../../navigation/routerContext';
import { withRouter } from '../../../../navigation/routerContext';
import { SpinErrorBoundary } from '../../../../presentation';

export class StepExecutionDetailsComponent extends React.Component<
  IExecutionDetailsProps & IRouterInjectedProps & IDeckRuntimeServicesInjectedProps,
  IExecutionDetailsState
> {
  constructor(props: IExecutionDetailsProps & IRouterInjectedProps & IDeckRuntimeServicesInjectedProps) {
    super(props);
    this.state = {
      configSections: this.getDetailsSections(props).map((s) => s.title),
      currentSection: null,
    };
  }

  private getDetailsSections(props: IExecutionDetailsProps) {
    return props.detailsSections.filter((section) => !section.shouldShow || section.shouldShow(props));
  }

  public updateCurrentSection(props: IExecutionDetailsProps & IRouterInjectedProps = this.props): void {
    const currentSection = props.stateParams.details;
    if (this.state.currentSection !== currentSection) {
      this.setState({ currentSection });
    }
  }

  public syncDetails(
    configSections: string[],
    props: IExecutionDetailsProps & IRouterInjectedProps & IDeckRuntimeServicesInjectedProps = this.props,
  ): void {
    props.deckRuntimeServices.executionDetailsSectionService.synchronizeSection(configSections, () =>
      this.updateCurrentSection(props),
    );
  }

  public componentDidMount(): void {
    this.syncDetails(this.state.configSections);
  }

  public componentWillReceiveProps(
    nextProps: IExecutionDetailsProps & IRouterInjectedProps & IDeckRuntimeServicesInjectedProps,
  ): void {
    const configSections = this.getDetailsSections(nextProps).map((s) => s.title);
    if (!isEqual(this.state.configSections, configSections)) {
      this.setState({ configSections });
      this.syncDetails(configSections, nextProps);
    }
    this.updateCurrentSection(nextProps);
  }

  public render() {
    const { configSections, currentSection } = this.state;
    const detailsSections = this.getDetailsSections(this.props);
    return (
      <div>
        <ExecutionDetailsSectionNav sections={configSections} />
        {detailsSections.map((Section) => (
          <SpinErrorBoundary category="StepExecutionDetails.Section" key={Section.title}>
            <Section name={Section.title} current={currentSection} {...this.props} />
          </SpinErrorBoundary>
        ))}
      </div>
    );
  }
}

export const StepExecutionDetails = withDeckRuntimeServices(withRouter(StepExecutionDetailsComponent));
