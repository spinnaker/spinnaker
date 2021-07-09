import React from 'react';

import { Applications } from './Applications';
import { Clusters } from './Clusters';
import { Pipelines } from './Pipelines';
import { ProjectAttributes } from './ProjectAttributes';
import { AccountService, IAccount } from '../../account';
import { ApplicationReader, IApplicationSummary } from '../../application';
import { IProject } from '../../domain';
import { WizardModal, WizardPage } from '../../modal';
import { IModalComponentProps, ReactModal } from '../../presentation';
import { ProjectReader } from '../service/ProjectReader';
import { ProjectWriter } from '../service/ProjectWriter';
import { TaskMonitor } from '../../task';
import { noop } from '../../utils';

import './ConfigureProjectModal.css';

export interface IConfigureProjectModalProps extends IModalComponentProps {
  title: string;
  projectConfiguration: IProject;
}

export interface IConfigureProjectModalState {
  allAccounts: IAccount[];
  allProjects: IProject[];
  allApplications: IApplicationSummary[];
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

  public componentDidMount() {
    this.initialFetch().then(() => this.setState({ loading: false }));
  }

  private submit = (project: IProject) => {
    const { name } = project;
    const taskMonitor = new TaskMonitor({
      title: 'Updating Project',
      modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.closeModal({ name, action: 'upsert' })),
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
        allProjects = allProjects.filter((project) => project.name !== currentProject);
        this.setState({ allAccounts, allApplications, allProjects });
      },
    );
  }

  private onDelete = () => {
    const { projectConfiguration } = this.props;
    const { name } = projectConfiguration;
    if (projectConfiguration) {
      const taskMonitor = new TaskMonitor({
        title: 'Deleting Project',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.closeModal({ name, action: 'delete' })),
      });

      this.setState({ taskMonitor });

      taskMonitor.submit(() => ProjectWriter.deleteProject(projectConfiguration));
    }
  };

  public render() {
    const { dismissModal, projectConfiguration } = this.props;
    const { allAccounts, allApplications, allProjects, loading, taskMonitor } = this.state;
    const appNames = allApplications.map((app) => app.name);

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
              render={({ innerRef }) => <Applications ref={innerRef} formik={formik} allApplications={appNames} />}
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
              render={({ innerRef }) => <Pipelines ref={innerRef} formik={formik} />}
            />
          </>
        )}
      />
    );
  }
}
