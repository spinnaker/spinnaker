import React from 'react';

interface IPipelineConfigActionProps {
  action?: () => void;
  name: string;
}

export function PipelineConfigAction(props: IPipelineConfigActionProps) {
  return (
    <li>
      <a className={props.action ? 'clickable' : 'disable'} onClick={props.action}>
        {props.name}
      </a>
    </li>
  );
}
