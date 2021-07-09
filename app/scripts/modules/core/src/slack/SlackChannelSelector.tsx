import React from 'react';
import { Option } from 'react-select';

import { ISlackChannel, SlackReader } from './SlackReader';
import { ReactSelectInput, useLatestPromise } from '../index';

export interface ISlackChannelSelectorProps {
  channel: ISlackChannel;
  callback: (name: string, value: any) => void;
}

export interface ISlackChannelSelectorState {
  channels: ISlackChannel[];
  selected: ISlackChannel;
  loading: boolean;
}

export default function SlackChannelSelector({ channel, callback }: ISlackChannelSelectorProps) {
  const [selectedChannel, setSelectedChannel] = React.useState(channel);
  const fetchChannels = useLatestPromise(() => SlackReader.getChannels(), []);
  const channels = fetchChannels.result;
  const isLoading = fetchChannels.status === 'PENDING';

  const onInputChange = (evt: Option<ISlackChannel>) => {
    const newChannel = evt ? evt.target.value : null;
    callback('slackChannel', newChannel || {});
    setSelectedChannel(newChannel);
  };

  return (
    <div className="form-group row">
      <div className="col-sm-3 sm-label-right">Slack Channel</div>
      <div className="col-sm-9">
        <ReactSelectInput
          inputClassName="form-control input-sm"
          mode="VIRTUALIZED"
          options={(channels || []).map((ch: ISlackChannel) => ({ value: ch, label: ch.name }))}
          value={selectedChannel && { value: selectedChannel, label: selectedChannel.name }}
          onChange={onInputChange}
          isLoading={isLoading}
        />
      </div>
    </div>
  );
}
