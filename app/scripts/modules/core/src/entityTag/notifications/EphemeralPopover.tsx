import * as React from 'react';

import { HoverablePopover } from 'core/presentation';
import { IEntityTags } from 'core/domain';

import * as moment from 'moment';

export interface IEphemeralPopoverProps {
  entity?: any;
}

export interface IEphemeralPopoverState {
  ttl?: number;
}

export class EphemeralPopover extends React.Component<IEphemeralPopoverProps, IEphemeralPopoverState> {
  constructor(props: IEphemeralPopoverProps) {
    super(props);
    this.state = this.getState(props);
  }

  private getState(props: IEphemeralPopoverProps): IEphemeralPopoverState {
    const entityTags: IEntityTags = props.entity && props.entity.entityTags;
    if (entityTags) {
      const ephemeralTag = entityTags.tags.filter(x => x.name === 'spinnaker:ttl')[0];
      if (ephemeralTag) {
        return {
          ttl: ephemeralTag.value.expiry,
        };
      }
    }

    return {};
  }

  private PopoverContent = () => {
    const { ttl } = this.state;
    const isInPast = !!ttl && Date.now() > ttl;
    const ttlPhrase = moment(ttl).fromNow();

    return (
      <div>
        This server group {isInPast ? 'was scheduled to be' : 'will be'} automatically destroyed{' '}
        <strong>{ttlPhrase}</strong>.
      </div>
    );
  };

  public componentWillReceiveProps(nextProps: IEphemeralPopoverProps): void {
    this.setState(this.getState(nextProps));
  }

  public render(): React.ReactElement<EphemeralPopover> {
    const { ttl } = this.state;
    if (!ttl) {
      return null;
    }

    return (
      <span className="tag-marker small">
        <HoverablePopover
          Component={this.PopoverContent}
          title="Ephemeral Server Group"
          className={`ephemeral-popover`}
        >
          <i className={`ephemeral fa fa-clock`} />
        </HoverablePopover>
      </span>
    );
  }
}
