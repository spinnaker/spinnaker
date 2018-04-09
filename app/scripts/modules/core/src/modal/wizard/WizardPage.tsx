import * as React from 'react';
import * as classNames from 'classnames';
import { BindAll } from 'lodash-decorators';

import { noop } from 'core/utils';

export interface IWizardPageProps {
  mandatory?: boolean;
  dirty?: boolean;
  dontMarkCompleteOnView?: boolean;
  done?: boolean;
  onMount?: (self: IWrappedWizardPage) => void;
  dirtyCallback?: (name: string, dirty: boolean) => void;
  ref?: () => void;
  revalidate?: () => void;
  setWaiting?: (section: string, isWaiting: boolean) => void;
}

export interface IWizardPageState {
  label: string;
}

export type IWizardPageValidate = (values: { [key: string]: any }) => { [key: string]: string };
export type IWrappedWizardPage = (React.ComponentClass<IWizardPageProps> | React.SFC<IWizardPageProps>) & {
  LABEL: string;
};

export function wizardPage<P = {}>(
  WrappedComponent: IWrappedWizardPage,
): React.ComponentClass<P & IWizardPageProps> & { label: string } {
  @BindAll()
  class WizardPage extends React.Component<P & IWizardPageProps, IWizardPageState> {
    public static defaultProps: Partial<IWizardPageProps> = {
      dirtyCallback: noop,
    };
    public static label = WrappedComponent.LABEL;

    public element: any;
    public validate: IWizardPageValidate;

    constructor(props: P & IWizardPageProps) {
      super(props);
      this.state = {
        label: WizardPage.label,
      };
    }

    public componentDidMount(): void {
      this.props.onMount(this as any);
    }

    public componentWillUnmount(): void {
      this.props.onMount(undefined);
    }

    private handleRef(element: any) {
      if (element) {
        this.element = element;
      }
    }

    private handleWrappedRef(wrappedComponent: any) {
      if (wrappedComponent) {
        this.validate = wrappedComponent.validate;
      }
    }

    public render() {
      const { dirtyCallback, dirty, done, mandatory } = this.props;
      const { label } = this.state;
      const showDone = done || !mandatory;
      const className = classNames({
        default: !showDone,
        dirty,
        done: showDone,
      });

      return (
        <div className="modal-page" ref={this.handleRef}>
          <div className="wizard-subheading sticky-header">
            <h4 className={className}>{label}</h4>
          </div>
          <div className="wizard-page-body">
            <WrappedComponent {...this.props} dirtyCallback={dirtyCallback} ref={this.handleWrappedRef} />
          </div>
        </div>
      );
    }
  }
  return WizardPage as any;
}
