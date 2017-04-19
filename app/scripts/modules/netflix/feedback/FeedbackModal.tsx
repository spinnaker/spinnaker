import * as React from 'react';
import { $location, $http } from 'ngimport';
import { Button, Modal } from 'react-bootstrap';

import { NetflixSettings } from '../netflix.settings';
import { SubmitButton } from 'core/modal/buttons/SubmitButton';
import { authenticationService } from 'core/authentication/authentication.service';

const states = {
  EDITING: 0,
  SUBMITTING: 1,
  SUBMITTED: 2,
  ERROR: 3
};

interface IFeedback {
  title: string;
  description: string;
  contact: string;
}

interface IFeedbackModalProps {
  show: boolean;
  // Since modal open/close can be controlled from outside the modal as well,
  // we can _only_ control the state from outside (one-way binding) so provide
  // a callback to set the open state
  showCallback: (show: boolean) => void;
}

interface IFeedbackModalState {
  feedbackState: number;
  userIsAuthenticated: boolean;
  feedback: IFeedback;
  issueUrl?: string;
  issueId?: string;
}

export class FeedbackModal extends React.Component<IFeedbackModalProps, IFeedbackModalState> {
  public static defaultProps: IFeedbackModalProps = {
    show: false,
    showCallback: () => {}
  };

  constructor(props: IFeedbackModalProps) {
    super(props);
    this.state = {
      feedbackState: states.EDITING,
      userIsAuthenticated: authenticationService.getAuthenticatedUser().authenticated,
      feedback: {
        title: '',
        description: '',
        contact: ''
      }
    };
  }

  private getContactInfo(): string {
    if (this.state.userIsAuthenticated) {
      return authenticationService.getAuthenticatedUser().name;
    }
    return this.state.feedback.contact;
  }

  private getUserNameFromContactInfo(): string {
    const email = this.getContactInfo();
    if (email.includes('@')) {
      return email.split('@')[0];
    }
    return email;
  }

  private buildDescription(): string {
    return [
      '*Submitted by:*\n' + this.getContactInfo(),
      '*From page:*\n' + $location.absUrl(),
      '*Description:*\n' + this.state.feedback.description
    ].join('\n\n');
  }

  private buildRequestBody(): IFeedback {
    return {
      title: this.state.feedback.title,
      description: this.buildDescription(),
      contact: this.getUserNameFromContactInfo(),
    };
  }

  private submit(): void {
    this.setState({ feedbackState: states.SUBMITTING });
    $http.post(NetflixSettings.feedbackUrl, this.buildRequestBody())
      .then((result: any) => {
        this.setState({
          feedbackState: states.SUBMITTED,
          issueUrl: result.data.url,
          issueId: result.data.id
        });
      },
      () => {
        this.setState({
          feedbackState: states.ERROR
        });
      });
  };

  private close = (): void => {
    this.props.showCallback(false);
  }

  private handleFeedbackChange(e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>, field: string): void {
    // TOOD: Use immutablejs
    const updatedFeedback: any = {};
    updatedFeedback[field] = e.target.value;
    this.setState({ feedback: Object.assign({}, this.state.feedback, updatedFeedback)});
  }

  private handleTitleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    this.handleFeedbackChange(e, 'title');
  }

  private handleDescriptionChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    this.handleFeedbackChange(e, 'description');
  }

  private handleContactChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    this.handleFeedbackChange(e, 'contact');
  }

  public render() {
    const formValid = this.state.feedback.title.length > 0 &&
                      this.state.feedback.description.length > 0;

    const submitting = this.state.feedbackState === states.SUBMITTING;

    return (
      <Modal show={this.props.show} onHide={this.close}>
        <Modal.Header closeButton={true}>
          <Modal.Title>Talk to Us</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {this.state.feedbackState !== states.SUBMITTED && (
            <form role="form" name="form">
              <p>
                Having a problem or looking for something that's not in Spinnaker? Let us know.
              </p>
              <p>
                You'll be able to <a target="_blank" href="https://jira.netflix.com/browse/SPIN">track the issue through JIRA</a>.</p>
              <br/>
              <div className="form-group">
                <label>Title</label>
                <input type="text" className="form-control"
                        value={this.state.feedback.title}
                        onChange={this.handleTitleChange}
                        placeholder="How can we help you?"
                        required={true}/>
              </div>
              <div className="form-group">
                <label>Description</label>
                <textarea value={this.state.feedback.description}
                          onChange={this.handleDescriptionChange}
                          className="form-control"
                          rows={4}
                          placeholder="Please be detailed and mention any steps required to reproduce the issue. Or just tell us what feature you want!"
                          required={true}
                  />
              </div>
              { !this.state.userIsAuthenticated && (
                <div className="form-group">
                  <label>Contact Info</label>
                  <input type="text"
                          className="form-control"
                          value={this.state.feedback.contact}
                          onChange={this.handleContactChange}
                          placeholder="Name or email address"
                          required={true}/>
                </div>
              )}
              { !formValid && (
                <div className="form-group">
                  <p className="warning-text text-right"><strong><span className="glyphicon glyphicon-asterisk"/> All fields are required.</strong></p>
                </div>
              )}
              { this.state.feedbackState === states.ERROR && (
                <div className="alert alert-danger">
                  <h3>Something went horribly wrong.</h3>
                  <p>
                    Really sorry - you can try to submit this again, or send us an email at:
                    <br/> delivery-engineering@netflix.com <br/>
                    and we'll try to get this straightened out as soon as possible.
                  </p>
                </div>
              )}
            </form>
          )}
          {this.state.feedbackState === states.SUBMITTED && (
            <div>
              <h3>Thanks for your feedback!</h3>
              <p>
                You can follow the progress of your issue { this.state.issueId && (<span>({this.state.issueId}) </span>)}
                <a target="_blank" href={this.state.issueUrl}>here</a>.
              </p>
            </div>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button onClick={this.close}>{this.state.feedbackState === states.EDITING ? 'Cancel' : 'Close'}</Button>
          {this.state.feedbackState !== states.SUBMITTED && (
            <SubmitButton label="Submit issue" onClick={this.submit} submitting={submitting} isDisabled={!(formValid && !submitting)}/>
          )}
        </Modal.Footer>
      </Modal>
    );
  }
}
