import React from 'react';

interface INotFoundProps {
  type: string;
  entityId: string;
}

export function NotFound(props: INotFoundProps) {
  return (
    <div className="application">
      <div>
        <h2 className="text-center">{props.type} Not Found</h2>
        <p className="text-center" style={{ marginBottom: '20px' }}>
          Please check your URL - we can't find any data for <em>{props.entityId}</em>.
        </p>
      </div>
    </div>
  );
}
