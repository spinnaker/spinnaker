import React from 'react';

export interface IKayentaStageConfigSectionProps {
  title: string;
  header?: React.ReactNode;
  children: React.ReactNode;
}

export function KayentaStageConfigSection({ title, header, children }: IKayentaStageConfigSectionProps) {
  return (
    <section>
      <ul className="list-inline">
        <li>
          <h5>{title}</h5>
        </li>
        <li>{header}</li>
      </ul>
      <div className="horizontal-rule" />
      {children}
    </section>
  );
}
