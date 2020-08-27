import React from 'react';
import { Subscription } from 'rxjs';

import { INotifier, NotifierService } from './notifier.service';
import { Markdown } from 'core/presentation';

import './notifier.component.less';

export interface INotifierState {
  messages: INotifier[];
}

export class Notifier extends React.Component<{}, INotifierState> {
  private subscription: Subscription;

  constructor(props: {}) {
    super(props);
    this.state = { messages: [] };
  }

  public componentDidMount() {
    this.subscription = NotifierService.messageStream.subscribe(message => {
      if (message.action === 'remove') {
        this.dismiss(message.key);
      } else {
        const existing = this.state.messages.find(m => m.key === message.key);
        if (existing) {
          existing.body = message.body;
          this.setState({ messages: this.state.messages });
        } else {
          this.setState({ messages: this.state.messages.concat([message]) });
        }
      }
    });
  }

  public componentWillUnmount() {
    this.subscription && this.subscription.unsubscribe();
  }

  private dismiss(key: string): void {
    this.setState({ messages: this.state.messages.filter(m => m.key !== key) });
  }

  private makeNotification = (message: INotifier) => (
    <div key={message.key} className="user-notification horizontal space-around">
      {message.content ? (
        <div className="message">{message.content}</div>
      ) : (
        <Markdown className="message" message={message.body} options={{ ADD_ATTR: ['onclick'] }} />
      )}
      <button className="btn btn-link close-notification" role="button" onClick={() => this.dismiss(message.key)}>
        <span className="fa fa-times" />
      </button>
    </div>
  );

  public render() {
    return <div className="user-notifications">{this.state.messages.map(this.makeNotification)}</div>;
  }
}
