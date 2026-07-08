import _ from 'lodash';

import { AccountService } from '@spinnaker/core';

import { dcosImageReader } from '../../image/image.reader';

function configureCommand(application, command, query = '') {
  const queries = command.docker.image ? [grabImageAndTag(command.docker.image.imageId)] : [];

  if (query) {
    queries.push(query);
  }

  let imagesPromise;
  if (queries.length) {
    imagesPromise = Promise.all(
      queries.map((q) =>
        dcosImageReader.findImages({
          provider: 'dockerRegistry',
          count: 50,
          q: q,
        }),
      ),
    ).then(_.flatten);
  } else {
    imagesPromise = Promise.resolve([]);
  }

  return Promise.all([AccountService.getCredentialsKeyedByAccount('dcos'), imagesPromise])
    .then(([credentialsKeyedByAccount, allImages]) => ({ credentialsKeyedByAccount, allImages }))
    .then(function (backingData) {
      backingData.accounts = _.keys(backingData.credentialsKeyedByAccount);
      backingData.filtered = {};
      backingData.allSecrets = {};

      if (application.attributes && application.attributes.secrets) {
        backingData.allSecrets = JSON.parse(application.attributes.secrets);
      }

      if (command.viewState.contextImages) {
        backingData.allImages = backingData.allImages.concat(command.viewState.contextImages);
      }

      command.backingData = backingData;

      return Promise.resolve().then(function () {
        configureAccount(command);
        attachEventHandlers(command);
      });
    });
}

function grabImageAndTag(imageId) {
  return imageId.split('/').pop();
}

function buildImageId(image) {
  if (image.fromContext) {
    return `${image.cluster} ${image.pattern}`;
  } else if (image.fromTrigger && !image.tag) {
    return `${image.registry}/${image.repository} (Tag resolved at runtime)`;
  } else {
    return `${image.registry}/${image.repository}:${image.tag}`;
  }
}

function configureDockerRegistries(command) {
  const result = { dirty: {} };

  const selectedDcosCluster = _.find(command.backingData.filtered.dcosClusters, { name: command.dcosCluster });
  if (selectedDcosCluster) {
    command.backingData.filtered.dockerRegistries = selectedDcosCluster.dockerRegistries;
  } else {
    command.backingData.filtered.dockerRegistries = [];
  }

  return result;
}

function configureImages(command) {
  const result = { dirty: {} };

  const registryAccountNames = _.map(command.backingData.filtered.dockerRegistries, function (registry) {
    return registry.accountName;
  });
  command.backingData.filtered.images = _.map(
    _.filter(command.backingData.allImages, function (image) {
      return image.fromContext || image.fromTrigger || _.includes(registryAccountNames, image.account) || image.message;
    }),
    function (image) {
      return mapImage(image);
    },
  );

  if (command.docker.image && !_.some(command.backingData.filtered.images, { imageId: command.docker.image.imageId })) {
    result.dirty.imageId = command.docker.image.imageId;
    command.docker.image = null;
  }

  return result;
}

function mapImage(image) {
  if (image.message !== undefined) {
    return image;
  }

  return {
    repository: image.repository,
    tag: image.tag,
    imageId: buildImageId(image),
    registry: image.registry,
    fromContext: image.fromContext,
    fromTrigger: image.fromTrigger,
    cluster: image.cluster,
    account: image.account,
    pattern: image.pattern,
    stageId: image.stageId,
  };
}

function configureSecrets(command) {
  const result = { dirty: {} };

  if (!command.region || !command.backingData.allSecrets[command.dcosCluster]) {
    command.backingData.filtered.secrets = [];
  } else {
    const appPath = command.account + '/' + command.region + '/';

    command.backingData.filtered.secrets = _.filter(
      command.backingData.allSecrets[command.dcosCluster].sort(),
      function (secret) {
        const secretPath = secret.substring(0, secret.lastIndexOf('/') + 1);
        return appPath.startsWith(secretPath);
      },
    );
  }

  if (command.viewModel.env) {
    command.viewModel.env.forEach(function (envModel) {
      if (
        envModel.isSecret &&
        envModel.rawValue != null &&
        !command.backingData.filtered.secrets.includes(envModel.rawValue)
      ) {
        result.dirty.secrets = result.dirty.secrets || [];
        result.dirty.secrets.push(envModel.rawValue);
        envModel.rawValue = null;
        envModel.value = null;
      }
    });
  }

  return result;
}

function configureDcosClusters(command) {
  const result = { dirty: {} };

  command.backingData.filtered.dcosClusters = command.backingData.account.dcosClusters;

  if (!_.chain(command.backingData.filtered.dcosClusters).some({ name: command.dcosCluster }).value()) {
    result.dirty.dcosCluster = command.dcosCluster;
    command.dcosCluster = null;
  }

  Object.assign(result.dirty, configureDockerRegistries(command).dirty);
  Object.assign(result.dirty, configureImages(command).dirty);
  Object.assign(result.dirty, configureSecrets(command).dirty);

  return result;
}

function configureAccount(command) {
  const result = { dirty: {} };

  command.backingData.account = command.backingData.credentialsKeyedByAccount[command.account];

  if (command.backingData.account) {
    Object.assign(result.dirty, configureDcosClusters(command).dirty);
  }

  return result;
}

function updateRegion(command) {
  command.region =
    command.dcosCluster + (command.group ? (command.group.substring(0, 1) == '/' ? '' : '/') + command.group : '');
}

function attachEventHandlers(command) {
  command.accountChanged = function accountChanged() {
    const result = { dirty: {} };
    Object.assign(result.dirty, configureAccount(command).dirty);
    command.viewState.dirty = command.viewState.dirty || {};
    Object.assign(command.viewState.dirty, result.dirty);
    return result;
  };

  command.dcosClusterChanged = function dcosClusterChanged() {
    updateRegion(command);

    const result = { dirty: {} };
    Object.assign(result.dirty, configureDockerRegistries(command).dirty);
    Object.assign(result.dirty, configureSecrets(command).dirty);
    command.viewState.dirty = command.viewState.dirty || {};
    Object.assign(command.viewState.dirty, result.dirty);
    return result;
  };

  command.groupChanged = function groupChanged() {
    updateRegion(command);

    const result = { dirty: {} };
    Object.assign(result.dirty, configureSecrets(command).dirty);
    command.viewState.dirty = command.viewState.dirty || {};
    Object.assign(command.viewState.dirty, result.dirty);
    return result;
  };
}

export const dcosServerGroupConfigurationService = {
  configureCommand,
  configureAccount,
  configureImages,
  configureDockerRegistries,
  configureSecrets,
  buildImageId,
};
