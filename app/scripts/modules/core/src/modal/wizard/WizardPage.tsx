import { isEqual, isFunction } from 'lodash';
import React from 'react';

import { IWizardModalApi } from './WizardModal';

export interface IWizardPageComponent<T> {
  validate(values: T): { [key: string]: any };
}

export interface IWizardPageRenderProps {
  // TODO: try to enforce that the ref'd component extends IWizardPageComponent?
  // innerRef: React.RefObject<IWizardPageComponent<any>>;
  innerRef: React.RefObject<any>;
  onLoadingChanged(isLoading: boolean): void;
}

export interface IWizardPageProps {
  label?: string;
  order: number;
  note?: React.ReactNode;
  render: (props: IWizardPageRenderProps) => JSX.Element;
  wizard: IWizardModalApi;
}

interface IWizardPageState {
  errors: object;
  order: number;
  isLoading: boolean;
  status: WizardPageStatus;
}

export type WizardPageStatus = 'default' | 'error' | 'loading';

export class WizardPage<T> extends React.Component<IWizardPageProps, IWizardPageState> {
  public state: IWizardPageState = {
    errors: {},
    order: 0,
    isLoading: false,
    status: 'default',
  };

  public ref = React.createRef<HTMLDivElement>();
  private innerRef = React.createRef<IWizardPageComponent<T>>();

  public static getStatusClass(status: WizardPageStatus): string {
    const statusToCssClass = { error: 'dirty', loading: 'waiting', default: 'done' };
    return statusToCssClass[status] || statusToCssClass.default;
  }

  private computeStatus(errors: any, isLoading: boolean): WizardPageStatus {
    return Object.keys(errors).length ? 'error' : isLoading ? 'loading' : 'default';
  }

  private onLoadingChanged = (isLoading: boolean) => {
    const status = this.computeStatus(this.state.errors, isLoading);
    return this.setState({ isLoading, status }, this.onWizardPageStateChanged);
  };

  public validate = (values: any) => {
    const component = this.innerRef.current;
    const errors = (component && isFunction(component.validate) && component.validate(values)) || {};

    if (!isEqual(errors, this.state.errors)) {
      // Save errors, notify Wizard
      const status = this.computeStatus(errors, this.state.isLoading);
      this.setState({ errors, status }, this.onWizardPageStateChanged);
    }

    return errors;
  };

  public componentDidMount(): void {
    this.props.wizard.onWizardPageAdded(this);
  }

  public componentWillUnmount(): void {
    this.props.wizard.onWizardPageRemoved(this);
  }

  public componentDidUpdate(prevProps: IWizardPageProps): void {
    // Update label or order if changed, notify Wizard
    if (this.props.order !== prevProps.order) {
      this.setState({ order: this.props.order }, this.onWizardPageStateChanged);
    }
  }

  private onWizardPageStateChanged() {
    this.props.wizard.onWizardPageStateChanged(this);
  }

  public render() {
    const { note, label, render } = this.props;
    const { status } = this.state;
    const { innerRef, onLoadingChanged } = this;

    const pageContents = render({ innerRef, onLoadingChanged });
    const className = WizardPage.getStatusClass(status);

    return (
      <div className="modal-page" ref={this.ref}>
        {label && (
          <div className="wizard-subheading sticky-header">
            <h4 className={className}>{label}</h4>
          </div>
        )}
        <div className="wizard-page-body">
          {pageContents}
          {note && <div className="row">{note}</div>}
        </div>
      </div>
    );
  }
}
