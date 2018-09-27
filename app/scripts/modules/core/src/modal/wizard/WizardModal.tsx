import * as React from 'react';
import * as classNames from 'classnames';
import { Formik, Form, FormikValues } from 'formik';
import { Modal } from 'react-bootstrap';

import { TaskMonitor } from 'core';
import { IModalComponentProps, Tooltip } from 'core/presentation';
import { NgReact } from 'core/reactShims';
import { Spinner } from 'core/widgets';

import { ModalClose } from '../buttons/ModalClose';
import { SubmitButton } from '../buttons/SubmitButton';

import { IWizardPageProps, IWizardPageValidate } from './WizardPage';

export interface IWizardPageData<T> {
  element: HTMLElement;
  label: string;
  props: IWizardPageProps<T>;
  validate: IWizardPageValidate;
}

export interface IWizardModalProps<T> extends IModalComponentProps {
  heading: string;
  hideSections?: Set<string>;
  initialValues: T;
  loading?: boolean;
  submitButtonLabel: string;
  taskMonitor: TaskMonitor;
  validate: IWizardPageValidate;
  closeModal?(result?: any): void; // provided by ReactModal
  dismissModal?(rejection?: any): void; // provided by ReactModal
}

export interface IWizardModalState<T> {
  currentPage: IWizardPageData<T>;
  dirtyPages: Set<string>;
  pageErrors: { [pageName: string]: { [key: string]: string } };
  formInvalid: boolean;
  pages: string[];
  waiting: Set<string>;
}

export class WizardModal<T = {}> extends React.Component<IWizardModalProps<T>, IWizardModalState<T>> {
  private pages: { [label: string]: IWizardPageData<T> } = {};
  private stepsElement: HTMLDivElement;

  constructor(props: IWizardModalProps<T>) {
    super(props);

    this.state = {
      currentPage: null,
      dirtyPages: new Set<string>(),
      formInvalid: false,
      pages: [],
      pageErrors: {},
      waiting: new Set(),
    };
  }

  private setCurrentPage = (pageState: IWizardPageData<T>): void => {
    if (this.stepsElement) {
      this.stepsElement.scrollTop = pageState.element.offsetTop;
    }
    this.setState({ currentPage: pageState });
  };

  private onMount = (element: any): void => {
    if (element) {
      const label = element.state.label;
      this.pages[label] = {
        element: element.element,
        label,
        validate: element.validate,
        props: element.props,
      };
    }
  };

  private dirtyCallback = (name: string, dirty: boolean): void => {
    const dirtyPages = new Set(this.state.dirtyPages);
    if (dirty) {
      dirtyPages.add(name);
    } else {
      dirtyPages.delete(name);
    }
    this.setState({ dirtyPages });
  };

  public componentDidMount(): void {
    const pages = this.getVisiblePageNames();
    this.setState({ pages: this.getVisiblePageNames(), currentPage: this.pages[pages[0]] });
  }

  public componentWillReceiveProps(): void {
    this.setState({ pages: this.getVisiblePageNames() });
  }

  public componentWillUnmount(): void {
    this.pages = {};
  }

  private handleStepsScroll = (event: React.UIEvent<HTMLDivElement>): void => {
    // Cannot precalculate because sections can shrink/grow.
    // Could optimize by having a callback every time the size changes... but premature
    const pageTops = this.state.pages.map(pageName => this.pages[pageName].element.offsetTop);
    const scrollTop = event.currentTarget.scrollTop;

    let reversedCurrentPage = pageTops.reverse().findIndex(pageTop => scrollTop >= pageTop);
    if (reversedCurrentPage === undefined) {
      reversedCurrentPage = pageTops.length - 1;
    }
    const currentPageIndex = pageTops.length - (reversedCurrentPage + 1);
    const currentPage = this.pages[this.state.pages[currentPageIndex]];

    this.setState({ currentPage });
  };

  private getFilteredChildren(): React.ReactChild[] {
    return React.Children.toArray(this.props.children).filter(
      (child: any): boolean => {
        if (!child || !child.type || !child.type.label) {
          return false;
        }
        return !this.props.hideSections || !this.props.hideSections.has(child.type.label);
      },
    );
  }

  private getVisiblePageNames(): string[] {
    return this.getFilteredChildren().map((child: any) => child.type.label);
  }

  private validate = (values: FormikValues): any => {
    const errors: Array<{ [key: string]: string }> = [];
    const newPageErrors: { [pageName: string]: { [key: string]: string } } = {};

    this.state.pages.forEach(pageName => {
      const pageErrors = this.pages[pageName].validate ? this.pages[pageName].validate(values) : {};
      if (Object.keys(pageErrors).length > 0) {
        newPageErrors[pageName] = pageErrors;
      } else {
        delete newPageErrors[pageName];
      }
      errors.push(pageErrors);
    });
    errors.push(this.props.validate(values));
    const flattenedErrors = Object.assign({}, ...errors);
    this.setState({ pageErrors: newPageErrors, formInvalid: Object.keys(flattenedErrors).length > 0 });
    return flattenedErrors;
  };

  private revalidate(values: FormikValues, setErrors: (errors: any) => void) {
    setErrors(this.validate(values));
  }

  private setWaiting = (section: string, isWaiting: boolean): void => {
    const waiting = new Set(this.state.waiting);
    isWaiting ? waiting.add(section) : waiting.delete(section);
    this.setState({ waiting });
  };

  public render() {
    const { heading, hideSections, initialValues, loading, submitButtonLabel, taskMonitor } = this.props;
    const { currentPage, dirtyPages, pageErrors, formInvalid, pages, waiting } = this.state;
    const { TaskMonitorWrapper } = NgReact;

    const pagesToShow = pages.filter(page => (!hideSections || !hideSections.has(page)) && this.pages[page]);

    const submitting = taskMonitor && taskMonitor.submitting;

    return (
      <>
        {taskMonitor && <TaskMonitorWrapper monitor={taskMonitor} />}
        <Formik<{}, T>
          initialValues={initialValues}
          onSubmit={this.props.closeModal}
          validate={this.validate}
          render={formik => (
            <Form className="form-horizontal">
              <ModalClose dismiss={this.props.dismissModal} />
              <Modal.Header>{heading && <h3>{heading}</h3>}</Modal.Header>
              <Modal.Body>
                {loading && (
                  <div className="row">
                    <Spinner size="large" />
                  </div>
                )}
                {!loading && (
                  <div className="row">
                    <div className="col-md-3 hidden-sm hidden-xs">
                      <ul className="steps-indicator wizard-navigation">
                        {pagesToShow.map(pageName => (
                          <WizardStepLabel<T>
                            key={this.pages[pageName].label}
                            current={this.pages[pageName] === currentPage}
                            dirty={dirtyPages.has(this.pages[pageName].label)}
                            errors={pageErrors[this.pages[pageName].label]}
                            pageState={this.pages[pageName]}
                            onClick={this.setCurrentPage}
                            waiting={waiting.has(pageName)}
                          />
                        ))}
                      </ul>
                    </div>
                    <div className="col-md-9 col-sm-12">
                      <div className="steps" ref={ele => (this.stepsElement = ele)} onScroll={this.handleStepsScroll}>
                        {this.getFilteredChildren().map((child: React.ReactElement<any>) => {
                          return React.cloneElement(child, {
                            formik: formik,
                            dirtyCallback: this.dirtyCallback,
                            onMount: this.onMount,
                            revalidate: () => this.revalidate(formik.values, formik.setErrors),
                            setWaiting: this.setWaiting,
                          });
                        })}
                      </div>
                    </div>
                  </div>
                )}
              </Modal.Body>
              <Modal.Footer>
                <button
                  className="btn btn-default"
                  disabled={submitting}
                  onClick={this.props.dismissModal}
                  type="button"
                >
                  Cancel
                </button>
                <SubmitButton
                  isDisabled={formInvalid || submitting || waiting.size > 0}
                  submitting={submitting}
                  isFormSubmit={true}
                  label={submitButtonLabel}
                />
              </Modal.Footer>
            </Form>
          )}
        />
      </>
    );
  }
}

interface IWizardStepLabelProps<T> {
  current: boolean;
  dirty: boolean;
  errors: { [key: string]: string };
  pageState: IWizardPageData<T>;
  onClick: (pageState: IWizardPageData<T>) => void;
  waiting: boolean;
}

class WizardStepLabel<T> extends React.Component<IWizardStepLabelProps<T>> {
  public render() {
    const { current, dirty, errors, onClick, pageState, waiting } = this.props;

    const className = classNames({
      default: !pageState.props.done,
      dirty: dirty || !!errors,
      current,
      done: pageState.props.done,
      waiting,
    });

    const label = (
      <li className={className}>
        <a className="clickable" onClick={() => onClick(pageState)}>
          {pageState.label}
        </a>
      </li>
    );

    if (errors) {
      const Errors = (
        <span>
          {Object.keys(errors).map(key => (
            <span key={key}>
              {errors[key]}
              <br />
            </span>
          ))}
        </span>
      );

      return <Tooltip template={Errors}>{label}</Tooltip>;
    }
    return label;
  }
}
