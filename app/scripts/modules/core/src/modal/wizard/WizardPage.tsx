import * as React from 'react';
import * as classNames from 'classnames';

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
  note?: React.ReactElement<any>;
}

export interface IWizardPageState {
  hasErrors: boolean;
  isDirty: boolean;
  label: string;
}

export type IWizardPageValidate = (values: { [key: string]: any }) => { [key: string]: string };
export type IWrappedWizardPage = React.ComponentClass<IWizardPageProps> & { LABEL: string };

export function wizardPage<P = {}>(
  WrappedComponent: IWrappedWizardPage,
): React.ComponentClass<P & IWizardPageProps> & { label: string } {
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
        hasErrors: false,
        isDirty: false,
        label: WizardPage.label,
      };
    }

    public componentDidMount(): void {
      this.props.onMount(this as any);
    }

    public componentWillUnmount(): void {
      this.props.onMount(undefined);
    }

    private dirtyCallback = (name: string, dirty: boolean): void => {
      if (name === this.state.label) {
        this.setState({ isDirty: dirty });
        this.props.dirtyCallback(name, dirty);
      }
    };

    private handleRef = (element: any) => {
      if (element) {
        this.element = element;
      }
    };

    private handleWrappedRef = (wrappedComponent: any) => {
      if (wrappedComponent) {
        this.validate = (values: { [key: string]: any }) => {
          const errors = wrappedComponent.validate(values);
          this.setState({ hasErrors: Object.keys(errors).length > 0 });
          return errors;
        };
      }
    };

    public render() {
      const { done, mandatory, note } = this.props;
      const { hasErrors, isDirty, label } = this.state;
      const showDone = done || !mandatory;
      const className = classNames({
        default: !showDone,
        dirty: hasErrors || isDirty,
        done: showDone,
      });

      return (
        <div className="modal-page" ref={this.handleRef}>
          <div className="wizard-subheading sticky-header">
            <h4 className={className}>{label}</h4>
          </div>
          <div className="wizard-page-body">
            <WrappedComponent {...this.props} dirtyCallback={this.dirtyCallback} ref={this.handleWrappedRef} />
            {note && <div className="row">{note}</div>}
          </div>
        </div>
      );
    }
  }
  return WizardPage as any;
}
