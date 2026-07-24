import React, { useEffect, useState } from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import { BakeryReader, HelpField } from '@spinnaker/core';

import type { IProxmoxTemplateImage } from '../../../image/proxmoxImage.reader';
import { listProxmoxTemplates } from '../../../image/proxmoxImage.reader';

interface IBaseImageOption {
  id: string;
  shortDescription?: string;
}

/**
 * Bake stage configuration. Rosco's proxmox handler clones the configured base template, runs the
 * packer provisioners, converts the result to a new template, and reports the new template's VMID
 * as the bake's image id — downstream Deploy stages pick it up automatically.
 */
export function ProxmoxBakeStageConfig({ stage, updateStage }: IStageConfigProps) {
  const [regions, setRegions] = useState<string[]>([]);
  const [baseImages, setBaseImages] = useState<IBaseImageOption[]>([]);
  const [templates, setTemplates] = useState<IProxmoxTemplateImage[]>([]);

  useEffect(() => {
    stage.cloudProviderType = 'proxmox';
    updateStage(stage);
    BakeryReader.getRegions('proxmox').then(setRegions);
    BakeryReader.getBaseOsOptions('proxmox').then((options) => setBaseImages(options?.baseImages ?? []));
    listProxmoxTemplates().then(setTemplates);
  }, []);

  const update = (field: string, value: any) => {
    stage[field] = value;
    updateStage(stage);
  };

  return (
    <div className="form-horizontal">
      <div className="form-group">
        <label className="col-md-3 sm-label-right">
          Node <HelpField content="Proxmox node (region) the bake runs on." />
        </label>
        <div className="col-md-7">
          {regions.length > 0 ? (
            <select
              className="form-control input-sm"
              value={stage.region ?? ''}
              onChange={(e) => update('region', e.target.value)}
            >
              <option value="" disabled={true}>
                Select a node
              </option>
              {regions.map((region) => (
                <option key={region} value={region}>
                  {region}
                </option>
              ))}
            </select>
          ) : (
            <input
              type="text"
              className="form-control input-sm"
              value={stage.region ?? ''}
              onChange={(e) => update('region', e.target.value)}
            />
          )}
        </div>
      </div>

      <div className="form-group">
        <label className="col-md-3 sm-label-right">
          Base OS <HelpField content="Base image configured in rosco's proxmox bakery defaults." />
        </label>
        <div className="col-md-7">
          <select
            className="form-control input-sm"
            value={stage.baseOs ?? ''}
            onChange={(e) => update('baseOs', e.target.value)}
          >
            <option value="" disabled={true}>
              Select a base OS
            </option>
            {baseImages.map((image) => (
              <option key={image.id} value={image.id}>
                {image.shortDescription ? `${image.id} — ${image.shortDescription}` : image.id}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="form-group">
        <label className="col-md-3 sm-label-right">
          Base Template{' '}
          <HelpField content="Optional: override the source template to clone. Defaults to the template configured for the selected base OS." />
        </label>
        <div className="col-md-7">
          <select
            className="form-control input-sm"
            value={stage.baseAmi ?? ''}
            onChange={(e) => update('baseAmi', e.target.value)}
          >
            <option value="">(use base OS default)</option>
            {templates.map((template) => (
              <option key={`${template.account}-${template.vmid}`} value={template.vmid}>
                {template.imageName} (vmid {template.vmid} on {template.region})
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="form-group">
        <label className="col-md-3 sm-label-right">
          Package <HelpField content="Space-separated packages to install during the bake." />
        </label>
        <div className="col-md-7">
          <input
            type="text"
            className="form-control input-sm"
            value={stage.package ?? ''}
            onChange={(e) => update('package', e.target.value)}
          />
        </div>
      </div>

      <div className="form-group">
        <label className="col-md-3 sm-label-right">
          Template Name <HelpField content="Optional name override for the baked template." />
        </label>
        <div className="col-md-7">
          <input
            type="text"
            className="form-control input-sm"
            value={stage.amiName ?? ''}
            onChange={(e) => update('amiName', e.target.value)}
          />
        </div>
      </div>
    </div>
  );
}
