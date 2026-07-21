import { isEqual } from 'lodash';
import React from 'react';
import { AngularServices } from '../../../../angular/services';

import { ExecutionDetailsSectionNav } from '../../../details';
import type { IExecutionDetailsProps, IExecutionDetailsState } from '../../../../domain';
import { SpinErrorBoundary } from '../../../../presentation';

export class StepExecutionDetails extends React.Component<IExecutionDetailsProps, IExecutionDetailsState> {
  constructor(props: IExecutionDetailsProps) {
    super(props);
    this.state = {
      configSections: this.getDetailsSections(props).map((s) => s.title),
      currentSection: null,
    };
  }

  private getDetailsSections(props: IExecutionDetailsProps) {
    return props.detailsSections.filter((section) => !section.shouldShow || section.shouldShow(props));
  }

  public updateCurrentSection(): void {
    const currentSection = AngularServices.$stateParams.details;
    if (this.state.currentSection !== currentSection) {
      this.setState({ currentSection });
    }
  }

  public syncDetails(configSections: string[]): void {
    AngularServices.executionDetailsSectionService.synchronizeSection(configSections, () =>
      this.updateCurrentSection(),
    );
  }

  public componentDidMount(): void {
    this.syncDetails(this.state.configSections);
  }

  public componentWillReceiveProps(nextProps: IExecutionDetailsProps): void {
    const configSections = this.getDetailsSections(nextProps).map((s) => s.title);
    if (!isEqual(this.state.configSections, configSections)) {
      this.setState({ configSections });
      this.syncDetails(configSections);
    } else {
      this.updateCurrentSection();
    }
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
