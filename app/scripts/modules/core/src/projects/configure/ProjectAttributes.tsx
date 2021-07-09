import { FormikErrors, FormikProps } from 'formik';
import React from 'react';

import { IProject } from '../../domain';
import { IWizardPageComponent } from '../../modal';
import { FormField, FormikFormField, TextInput } from '../../presentation';

export interface IProjectAttributesProps {
  allProjects: IProject[];
  formik: FormikProps<IProject>;
  onDelete?: Function;
}

interface IProjectAttributesState {
  showProjectDeleteForm: boolean;
  projectNameForDeletion: string;
}

export class ProjectAttributes
  extends React.Component<IProjectAttributesProps, IProjectAttributesState>
  implements IWizardPageComponent<IProject> {
  public state = {
    showProjectDeleteForm: false,
    projectNameForDeletion: null as string,
  };

  public validate(values: IProject) {
    const errors: FormikErrors<IProject> = {};

    const isValidName = (name: string) => {
      const namePattern = /^[^\\^/?%#]*$/;
      return name.match(namePattern);
    };

    const isValidEmail = (email: string) => {
      const emailPattern = /^(.+)@(.+).([A-Za-z]{2,6})/;
      return email.match(emailPattern);
    };

    const { allProjects } = this.props;
    const allProjectNames = allProjects.map((project) => project.name.toLowerCase());

    if (!values.name) {
      errors.name = 'Please enter a project name';
    } else if (!isValidName(values.name)) {
      errors.name = 'Project name cannot contain any of the following characters:  / % #';
    } else if (allProjectNames.includes(values.name.toLowerCase())) {
      errors.name = 'There is already a project with that name';
    }

    if (!values.email) {
      errors.email = 'Please enter an email address';
    } else if (values.email && !isValidEmail(values.email)) {
      errors.email = 'Please enter a valid email address';
    }

    return errors;
  }

  private DeleteConfirmation = ({ projectName }: { projectName: string }) => {
    const { onDelete } = this.props;
    const { projectNameForDeletion } = this.state;
    const matchError = projectNameForDeletion !== projectName;

    const handleCancelClicked = () => this.setState({ showProjectDeleteForm: false, projectNameForDeletion: null });

    return (
      <div className="well">
        <p>{`Type the name of the project (${projectName}) below to continue.`}</p>

        <FormField
          input={(props) => <TextInput {...props} id="projectNameForDeletion" placeholder="Project Name" />}
          value={projectNameForDeletion || ''}
          onChange={(evt: any) => this.setState({ projectNameForDeletion: evt.target.value })}
          validate={() => matchError && 'Project name does not match'}
          touched={projectNameForDeletion != null}
          required={true}
        />

        <div className="sp-margin-m-top sp-group-margin-s-xaxis">
          <button type="button" className="passive" onClick={handleCancelClicked}>
            Cancel
          </button>

          <button type="button" className="primary" disabled={matchError} onClick={() => onDelete()}>
            <span className="glyphicon glyphicon-trash" /> Delete project
          </button>
        </div>
      </div>
    );
  };

  private DeleteButton = () => {
    const handleDeleteClicked = () => {
      this.setState({ showProjectDeleteForm: true }, () => document.getElementById('projectNameForDeletion').focus());
    };
    return (
      <button className="btn btn-default btn-sm" onClick={handleDeleteClicked}>
        <span className="glyphicon glyphicon-trash" /> Delete Project
      </button>
    );
  };

  public render() {
    const { formik } = this.props;
    const { DeleteConfirmation, DeleteButton } = this;

    return (
      <>
        <div className="sp-margin-m-bottom">
          <FormikFormField
            input={(props) => <TextInput {...props} placeholder="Project Name" />}
            name="name"
            label="Project Name"
            required={true}
          />
        </div>

        <div className="sp-margin-m-bottom">
          <FormikFormField
            input={(props) => <TextInput {...props} placeholder="Enter an email address" />}
            name="email"
            label="Owner Email"
            required={true}
          />
        </div>

        {this.state.showProjectDeleteForm ? <DeleteConfirmation projectName={formik.values.name} /> : <DeleteButton />}
      </>
    );
  }
}
