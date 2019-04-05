import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';
import { Option } from 'react-select';
import { Observable, Subject } from 'rxjs';

import { Application, ApplicationReader, IApplicationSummary } from 'core/application';
import ApplicationSelector from './ApplicationSelector';
import { CreatePipelineModal } from 'core/pipeline';
import { IPipelineTemplateV2 } from 'core/domain/IPipelineTemplateV2';
import { Spinner } from 'core/widgets/spinners/Spinner';
import { SubmitButton } from 'core/modal/buttons/SubmitButton';

export interface ICreatePipelineFromTemplateProps {
  closeModalCallback: () => void;
  template: IPipelineTemplateV2;
}

interface ICreatePipelineFromTemplateState {
  applicationError: string;
  applications: IApplicationSummary[];
  applicationSelectionComplete: boolean;
  loadedApplication: Application;
  loading: boolean;
  selectedApplication: IApplicationSummary;
  submitting: boolean;
}

export default class CreatePipelineFromTemplate extends React.Component<
  ICreatePipelineFromTemplateProps,
  ICreatePipelineFromTemplateState
> {
  public state: ICreatePipelineFromTemplateState = {
    applicationError: '',
    applicationSelectionComplete: false,
    applications: [],
    loading: true,
    loadedApplication: null,
    selectedApplication: null,
    submitting: false,
  };

  private destroy$ = new Subject();

  public componentDidMount() {
    Observable.fromPromise(ApplicationReader.listApplications())
      .takeUntil(this.destroy$)
      .subscribe(
        applications => this.setState({ applications, loading: false }),
        () => {
          this.setState({ applicationError: `Could not load application list. Please try again.`, loading: false });
        },
      );
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  public handleApplicationSelect = (selectedApplicationOption: Option): void => {
    if (selectedApplicationOption === null) {
      return this.setState({ applicationError: '', selectedApplication: null });
    }

    const selectedApplication =
      this.state.applications.find(
        ({ name }: IApplicationSummary): boolean => name === selectedApplicationOption.value,
      ) || null;
    this.setState({ applicationError: '', selectedApplication });
  };

  public handleLoadErrorDismissal = (): void => {
    this.setState({ applicationError: '' });
  };

  public continueWithCreatingPipeline = () => {
    const {
      selectedApplication: { name },
    } = this.state;

    this.setState({ submitting: true });
    Observable.fromPromise(ApplicationReader.getApplication(name))
      .takeUntil(this.destroy$)
      .subscribe(
        loadedApplication => {
          loadedApplication.getDataSource('pipelineConfigs').activate();
          this.setState({ applicationSelectionComplete: true, loadedApplication, submitting: false });
        },
        () => {
          this.setState({
            applicationError: `Could not load application ${name}. Please try again.`,
            selectedApplication: null,
            submitting: false,
          });
        },
      );
  };

  public render() {
    const {
      applications,
      applicationError,
      applicationSelectionComplete,
      loading,
      loadedApplication,
      selectedApplication,
      submitting,
    } = this.state;
    const { closeModalCallback, template } = this.props;

    if (applicationSelectionComplete) {
      return (
        <CreatePipelineModal
          application={loadedApplication}
          show={true}
          showCallback={closeModalCallback}
          pipelineSavedCallback={closeModalCallback}
          preselectedTemplate={template}
        />
      );
    }

    return (
      <Modal show={true} onHide={closeModalCallback}>
        <Modal.Header closeButton={true}>
          <Modal.Title>Select An Application</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {applicationError && (
            <div className="alert alert-danger">
              <p>{applicationError}</p>
              <p>
                <a onClick={this.handleLoadErrorDismissal}>[dismiss]</a>
              </p>
            </div>
          )}
          {loading ? (
            <Spinner size="medium" />
          ) : (
            <ApplicationSelector
              applications={applications}
              applicationSelectCallback={this.handleApplicationSelect}
              selectedApplication={selectedApplication}
            />
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button onClick={closeModalCallback}>Cancel</Button>
          <SubmitButton
            label="Continue"
            submitting={submitting}
            isDisabled={loading || submitting || selectedApplication === null}
            onClick={this.continueWithCreatingPipeline}
          />
        </Modal.Footer>
      </Modal>
    );
  }
}
