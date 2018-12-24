import * as React from 'react';

import { AccountService, IAccount } from 'core/account';
import { ApplicationReader, IApplicationSummary } from 'core/application';
import { IPipeline, IProject } from 'core/domain';
import { WizardModal, WizardPage } from 'core/modal';
import { PipelineConfigService } from 'core/pipeline';
import { IModalComponentProps, ReactModal } from 'core/presentation';
import { TaskMonitor } from 'core/task';
import { noop } from 'core/utils';
import { ReactInjector } from 'core/reactShims';

import { ProjectReader } from '../service/ProjectReader';
import { ProjectWriter } from '../service/ProjectWriter';

import { Applications } from './Applications';
import { Clusters } from './Clusters';
import { Pipelines } from './Pipelines';
import { ProjectAttributes } from './ProjectAttributes';

import './ConfigureProjectModal.css';

export interface IConfigureProjectModalProps extends IModalComponentProps {
  title: string;
  projectConfiguration: IProject;
}

export interface IConfigureProjectModalState {
  allAccounts: IAccount[];
  allProjects: IProject[];
  allApplications: IApplicationSummary[];
  appPipelines: {
    [appName: string]: IPipeline[];
  };
  configuredApps: string[];
  loading: boolean;
  taskMonitor?: TaskMonitor;
}

export class ConfigureProjectModal extends React.Component<IConfigureProjectModalProps, IConfigureProjectModalState> {
  public static defaultProps: Partial<IConfigureProjectModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public state: IConfigureProjectModalState = {
    loading: true,
    allAccounts: [],
    allProjects: [],
    allApplications: [],
    appPipelines: {},
    configuredApps: [],
  };

  public static show(props?: IConfigureProjectModalProps): Promise<any> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    if (props) {
      return ReactModal.show(ConfigureProjectModal, props, modalProps);
    }

    const title = 'Create Project';
    const projectConfiguration = {
      config: {
        applications: [],
        clusters: [],
        pipelineConfigs: [],
      },
      email: '',
      name: '',
    } as IProject;

    const projectProps = { title, projectConfiguration } as IConfigureProjectModalProps;
    return ReactModal.show(ConfigureProjectModal, projectProps, modalProps);
  }

  private handleApplicationsChanged = (configuredApps: string[]) => {
    this.setState({ configuredApps });
    this.fetchPipelinesForApps(configuredApps);
  };

  public componentDidMount() {
    const { projectConfiguration } = this.props;
    const configuredApps = (projectConfiguration && projectConfiguration.config.applications) || [];
    Promise.all([this.fetchPipelinesForApps(configuredApps), this.initialFetch()]).then(() =>
      this.setState({ loading: false, configuredApps }),
    );
  }

  private submit = (project: IProject) => {
    const taskMonitor = new TaskMonitor({
      title: 'Updating Project',
      onTaskComplete: () => ReactInjector.$state.go('home.project', { project: project.name }),
      modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
    });

    this.setState({ taskMonitor });

    taskMonitor.submit(() => ProjectWriter.upsertProject(project));
  };

  private initialFetch(): Promise<any> {
    const fetchAccounts = AccountService.listAccounts();
    const fetchApps = ApplicationReader.listApplications();
    const fetchProjects = ProjectReader.listProjects();
    const currentProject = this.props.projectConfiguration.name;
    return Promise.all([fetchAccounts, fetchApps, fetchProjects]).then(
      ([allAccounts, allApplications, allProjects]) => {
        allProjects = allProjects.filter(project => project.name !== currentProject);
        this.setState({ allAccounts, allApplications, allProjects });
      },
    );
  }

  private fetchPipelinesForApps = (applications: string[]) => {
    // Only fetch for apps we don't already have results for
    const appsToFetch = applications.filter(appName => !this.state.appPipelines[appName]);

    const fetches = appsToFetch.map(appName => {
      return PipelineConfigService.getPipelinesForApplication(appName).then(pipelines =>
        this.setState({
          appPipelines: { ...this.state.appPipelines, [appName]: pipelines },
        }),
      );
    });

    return Promise.all(fetches);
  };

  private onDelete = () => {
    const { projectConfiguration } = this.props;
    if (projectConfiguration) {
      const taskMonitor = new TaskMonitor({
        title: 'Deleting Project',
        onTaskComplete: () => ReactInjector.$state.go('home.search'),
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
      });

      this.setState({ taskMonitor });

      taskMonitor.submit(() => ProjectWriter.deleteProject(projectConfiguration));
    }
  };

  public render() {
    const { dismissModal, projectConfiguration } = this.props;
    const { allAccounts, allApplications, allProjects, appPipelines, loading, taskMonitor } = this.state;
    const appNames = allApplications.map(app => app.name);

    return (
      <WizardModal<IProject>
        closeModal={this.submit}
        dismissModal={dismissModal}
        heading="Configure Project"
        initialValues={projectConfiguration}
        loading={loading}
        submitButtonLabel="Save"
        taskMonitor={taskMonitor}
        render={({ nextIdx, wizard, formik }) => (
          <>
            <WizardPage
              label="Project Attributes"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => (
                <ProjectAttributes ref={innerRef} formik={formik} allProjects={allProjects} onDelete={this.onDelete} />
              )}
            />

            <WizardPage
              label="Applications"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => (
                <Applications
                  ref={innerRef}
                  formik={formik}
                  allApplications={appNames}
                  onApplicationsChanged={this.handleApplicationsChanged}
                />
              )}
            />

            <WizardPage
              label="Clusters"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <Clusters ref={innerRef} accounts={allAccounts} />}
            />

            <WizardPage
              label="Pipelines"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <Pipelines ref={innerRef} appsPipelines={appPipelines} />}
            />
          </>
        )}
      />
    );
  }
}
