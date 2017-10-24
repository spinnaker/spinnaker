import * as React from 'react';
import { isEqual } from 'lodash';

import { IExecutionDetailsComponentProps, IExecutionDetailsComponentState } from 'core/domain';
import { ReactInjector } from 'core/reactShims';

export function stageExecutionDetails(WrappedStageExecutionDetails: React.ComponentClass<IExecutionDetailsComponentProps>): React.ComponentClass<IExecutionDetailsComponentProps> {
  return class extends React.Component<IExecutionDetailsComponentProps, IExecutionDetailsComponentState> {
    constructor(props: IExecutionDetailsComponentProps) {
      super(props);
      this.state = {
        detailsSection: null,
      }
    }

    public updateDetailsSection(): void {
      const detailsSection = ReactInjector.$stateParams.details;
      if (this.state.detailsSection !== detailsSection) {
        this.setState({ detailsSection });
      }
    }

    public syncDetails(props: IExecutionDetailsComponentProps): void {
      ReactInjector.executionDetailsSectionService.synchronizeSection(props.configSections, () => this.updateDetailsSection());
    }

    public componentDidMount(): void {
      this.syncDetails(this.props);
    }

    public componentWillReceiveProps(nextProps: IExecutionDetailsComponentProps): void {
      this.syncDetails(nextProps);
    }

    public render() {
      return (
        <WrappedStageExecutionDetails detailsSection={this.state.detailsSection} {...this.props} />
      );
    }
  }
}
