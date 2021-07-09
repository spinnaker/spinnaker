import { Form, Formik, FormikProps } from 'formik';
import { merge, without } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { WizardPage } from './WizardPage';
import { WizardStepLabel } from './WizardStepLabel';
import { ModalClose } from '../buttons/ModalClose';
import { SubmitButton } from '../buttons/SubmitButton';
import { SpinFormik } from '../../presentation';
import { IModalComponentProps } from '../../presentation';
import { TaskMonitor, TaskMonitorWrapper } from '../../task';
import { Spinner } from '../../widgets';

export interface IWizardPageInjectedProps<T> {
  formik: FormikProps<T>;
  /** WizardModal supplies this incrementor fn, which should be used to supply the WizardPage order prop */
  nextIdx: () => number;
  /** The WizardModal Callback API for use by WizardPage component */
  wizard: IWizardModalApi;
}

export interface IWizardModalProps<T> extends IModalComponentProps {
  formClassName?: string;
  heading: string;
  initialValues: T;
  loading?: boolean;
  render: (props: IWizardPageInjectedProps<T>) => React.ReactNode;
  submitButtonLabel: string;
  taskMonitor: TaskMonitor;
  validate?(values: T): any;
}

export interface IWizardModalState<T> {
  currentPage: WizardPage<T>;
  initialized: boolean;
  pages: Array<WizardPage<T>>;
}

export interface IWizardModalApi {
  onWizardPageAdded: (wizardPage: WizardPage<any>) => void;
  onWizardPageRemoved: (wizardPage: WizardPage<any>) => void;
  /**
   * The wrapped WizardPage component can call this when its state changes
   * and WizardModal will force a re-render
   */
  onWizardPageStateChanged: (_page: WizardPage<any>) => void;
}

export class WizardModal<T = {}>
  extends React.Component<IWizardModalProps<T>, IWizardModalState<T>>
  implements IWizardModalApi {
  private stepsElement = React.createRef<HTMLDivElement>();
  private formikRef = React.createRef<Formik<any>>();
  public state: IWizardModalState<T> = { pages: [], initialized: false, currentPage: null };

  private static incrementer() {
    let idx = 0;
    return () => ++idx;
  }

  public get formik() {
    return this.formikRef.current && (this.formikRef.current.getFormikBag() as FormikProps<T>);
  }

  public componentDidMount(): void {
    this.setState({ initialized: true });
  }

  public onWizardPageAdded = (wizardPage: WizardPage<T>): void => {
    this.setState((prevState) => {
      const pages = prevState.pages.concat(wizardPage);
      const currentPage = prevState.currentPage || pages[0];
      return { pages, currentPage };
    }, this.revalidate);
  };

  public onWizardPageRemoved = (wizardPage: WizardPage<T>): void => {
    this.setState((prevState) => {
      const pages = without(prevState.pages, wizardPage);
      const currentPage = prevState.currentPage || pages[0];
      return { pages, currentPage };
    }, this.revalidate);
  };

  private setCurrentPage = (currentPage: WizardPage<T>): void => {
    if (currentPage && this.stepsElement.current && currentPage.ref.current) {
      this.stepsElement.current.scrollTop = currentPage.ref.current.offsetTop;
    }
    this.setState({ currentPage });
  };

  private handleStepsScroll = (event: React.UIEvent<HTMLDivElement>): void => {
    const pageTops = this.state.pages.map((page) => page.ref.current.offsetTop);
    const scrollTop = event.currentTarget.scrollTop;

    let reversedCurrentPage = pageTops.reverse().findIndex((pageTop) => scrollTop >= pageTop);
    if (reversedCurrentPage === undefined) {
      reversedCurrentPage = pageTops.length - 1;
    }
    const currentPageIndex = pageTops.length - (reversedCurrentPage + 1);
    const currentPage = this.state.pages[currentPageIndex];

    if (currentPage !== this.state.currentPage) {
      this.setState({ currentPage });
    }
  };

  private validate = (values: T): any => {
    const validateProp = this.props.validate || (() => ({}));
    const errorsForPages: object[] = this.state.pages.map((page) => page.validate(values)).concat(validateProp(values));
    return errorsForPages.reduce((mergedErrors, errorsForPage) => merge(mergedErrors, errorsForPage), {});
  };

  /** Rerender everything when a WizardPage requests it */
  public onWizardPageStateChanged(_page: WizardPage<T>) {
    this.forceUpdate();
  }

  public revalidate = () => this.formik && this.formik.validateForm();

  public render() {
    const {
      formClassName,
      heading,
      initialValues,
      loading,
      submitButtonLabel,
      taskMonitor,
      closeModal,
      dismissModal,
    } = this.props;
    const { currentPage, initialized, pages } = this.state;

    const spinner = (
      <div className="row">
        <Spinner size="large" />
      </div>
    );

    const pageLabels: React.ReactNode[] = pages
      .sort((a, b) => a.state.order - b.state.order)
      .map((page: WizardPage<T>) => (
        <WizardStepLabel
          key={page.props.label}
          current={page === currentPage}
          onClick={this.setCurrentPage}
          page={page}
        />
      ));

    const renderPageContents = () => {
      const formik = this.formik;
      const nextIdx = WizardModal.incrementer();
      return formik ? this.props.render({ formik, nextIdx, wizard: this }) : null;
    };

    const isSubmitting = taskMonitor && taskMonitor.submitting;
    const anyLoading = pages.some((page) => page.state.status === 'loading');

    return (
      <>
        {taskMonitor && <TaskMonitorWrapper monitor={taskMonitor} />}

        <SpinFormik<T>
          ref={this.formikRef}
          initialValues={initialValues}
          onSubmit={closeModal}
          validate={this.validate}
          render={(formik) => (
            <Form className={`form-horizontal ${formClassName}`}>
              <ModalClose dismiss={dismissModal} />
              <Modal.Header>{heading && <Modal.Title>{heading}</Modal.Title>}</Modal.Header>

              <Modal.Body>
                {loading || !initialized ? (
                  spinner
                ) : (
                  <div className="row">
                    <div className="col-md-3 hidden-sm hidden-xs">
                      <ul className="steps-indicator wizard-navigation">{pageLabels}</ul>
                    </div>
                    <div className="col-md-9 col-sm-12">
                      <div className="steps" ref={this.stepsElement} onScroll={this.handleStepsScroll}>
                        {renderPageContents()}
                      </div>
                    </div>
                  </div>
                )}
              </Modal.Body>

              <Modal.Footer>
                <button className="btn btn-default" disabled={isSubmitting} onClick={dismissModal} type="button">
                  Cancel
                </button>
                <SubmitButton
                  isDisabled={!formik.isValid || isSubmitting || anyLoading || loading}
                  submitting={isSubmitting}
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
