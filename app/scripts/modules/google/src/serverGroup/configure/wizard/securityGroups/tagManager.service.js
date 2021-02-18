'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { FirewallLabels } from '@spinnaker/core';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE =
  'spinnaker.deck.gce.tagManager.service';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE; // for backwards compatibility
module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE, []).factory('gceTagManager', function () {
  const resetKeys = ['command', 'securityGroups', 'securityGroupObjectsKeyedByTag', 'securityGroupObjectsKeyedById'];

  this.reset = () => {
    resetKeys.forEach((key) => delete this[key]);
  };

  this.register = (command) => {
    this.command = command;
    const { credentials, backingData } = command;
    if (backingData.securityGroups[credentials] !== undefined) {
      this.securityGroupObjects = _.cloneDeep(backingData.securityGroups[credentials].gce.global);

      initializeSecurityGroupObjects(this.securityGroupObjects, command.tags);

      const { byTag, bySecurityGroupId } = groupSecurityGroupObjects(this.securityGroupObjects);
      this.securityGroupObjectsKeyedByTag = byTag;
      this.securityGroupObjectsKeyedById = bySecurityGroupId;
    }

    if (!command.securityGroups) {
      command.securityGroups = this.inferSecurityGroupIdsFromTags(command.tags);
    }
  };

  this.inferSecurityGroupIdsFromTags = (commandTags = []) => {
    return _.chain(commandTags)
      .map((t) => this.securityGroupObjectsKeyedByTag[t.value])
      .flatten()
      .compact()
      .filter((sg) => sg.network === this.command.network)
      .map('id')
      .uniq()
      .value();
  };

  const initializeSecurityGroupObjects = (securityGroupObjects, commandTags = []) => {
    securityGroupObjects.forEach((sg) => {
      // Raw target tags look like '[tag-a, tag-b]'.
      const rawTags = sg.targetTags;
      sg.tagsArray = rawTags ? rawTags.substring(1, rawTags.length - 1).split(', ') : [];

      sg.selectedTags = commandTags
        ? _.intersection(
            commandTags.map((t) => t.value),
            sg.tagsArray,
          )
        : [];
    });
  };

  const groupSecurityGroupObjects = (securityGroupObjects) => {
    return securityGroupObjects.reduce(
      (groupings, sg) => {
        const { bySecurityGroupId } = groupings;
        bySecurityGroupId[sg.id] = sg;

        return sg.tagsArray.reduce((groupings, tag) => {
          const { byTag } = groupings;
          if (!byTag[tag]) {
            byTag[tag] = [];
          }
          byTag[tag].push(sg);

          return groupings;
        }, groupings);
      },
      { byTag: {}, bySecurityGroupId: {} },
    );
  };

  this.inferSelectedSecurityGroupFromTag = _.debounce((tagName) => {
    let securityGroupObjectsWithTag = this.securityGroupObjectsKeyedByTag[tagName];
    const c = this.command;

    if (securityGroupObjectsWithTag) {
      securityGroupObjectsWithTag = _.filter(securityGroupObjectsWithTag, (sg) => sg.network === this.command.network);
    }

    if (securityGroupObjectsWithTag) {
      updateSelectedTagsOnSecurityGroupObjects(securityGroupObjectsWithTag, tagName);
      c.securityGroups = _.map(
        updateSelectedSecurityGroups(getSecurityGroupObjectsFromIds(c.securityGroups), securityGroupObjectsWithTag),
        'id',
      );
    }

    // If you pause while typing "tag-a," but you really want to add "tag-abc,"
    // make sure you remove "tag-a".
    this.updateSelectedTags();
  }, 100);

  this.updateSelectedTags = () => {
    const c = this.command;
    const tags = c.tags.map((t) => t.value);

    getSecurityGroupObjectsFromIds(c.securityGroups).forEach((sg) => {
      if (sg.selectedTags) {
        sg.selectedTags = _.intersection(sg.selectedTags, tags);
      }

      if (!_.get(sg, 'selectedTags.length')) {
        c.securityGroups = _.without(c.securityGroups, sg.id);
      }
    });
  };

  this.addTag = (tagName) => {
    const c = this.command;
    const tags = c.tags;
    let securityGroupObjectsWithTag = this.securityGroupObjectsKeyedByTag[tagName];

    if (securityGroupObjectsWithTag) {
      securityGroupObjectsWithTag = _.filter(securityGroupObjectsWithTag, (sg) => sg.network === this.command.network);
    }

    if (
      !_.includes(
        tags.map((t) => t.value),
        tagName,
      )
    ) {
      tags.push({ value: tagName });
    }

    if (securityGroupObjectsWithTag) {
      updateSelectedTagsOnSecurityGroupObjects(securityGroupObjectsWithTag, tagName);
      c.securityGroups = _.map(
        updateSelectedSecurityGroups(getSecurityGroupObjectsFromIds(c.securityGroups), securityGroupObjectsWithTag),
        'id',
      );
    }
  };

  const getSecurityGroupObjectsFromIds = (ids) => ids.map((id) => this.securityGroupObjectsKeyedById[id]);

  const updateSelectedTagsOnSecurityGroupObjects = (securityGroupObjects, newTag) => {
    securityGroupObjects.forEach((sg) => {
      sg.selectedTags = _.chain(sg.selectedTags || [])
        .concat([newTag])
        .uniq()
        .value();
    });
  };

  const updateSelectedSecurityGroups = (oldGroups, newGroups) => {
    return _.chain(oldGroups).concat(newGroups).uniq().value();
  };

  this.removeTag = (tagName) => {
    const c = this.command;
    const securityGroupIds = c.securityGroups || [];
    const securityGroupObjects = getSecurityGroupObjectsFromIds(securityGroupIds);

    securityGroupObjects.forEach((sg) => {
      if (sg.selectedTags) {
        sg.selectedTags = sg.selectedTags.filter((tag) => tag !== tagName);
      }
    });

    c.tags = c.tags.filter((tag) => tag.value !== tagName);
  };

  this.removeSecurityGroup = (securityGroupId) => {
    const securityGroupObject = this.securityGroupObjectsKeyedById[securityGroupId];
    const tagsToRemove = securityGroupObject.selectedTags;
    const c = this.command;

    getSecurityGroupObjectsFromIds(c.securityGroups).forEach((sg) => {
      if (sg.selectedTags) {
        sg.selectedTags = _.difference(sg.selectedTags, tagsToRemove);
      }
    });

    securityGroupObject.selectedTags = [];
    c.tags = _.chain(c.tags)
      .map((t) => t.value)
      .difference(tagsToRemove)
      .map((t) => ({ value: t }))
      .value();
  };

  this.getToolTipContent = (tagName) => {
    const groups = _.get(this, ['securityGroupObjectsKeyedByTag', tagName]);
    const groupIds = groups ? groups.filter((sg) => sg.network === this.command.network).map((sg) => sg.id) : [];

    return `This tag associates this server group with ${
      groupIds.length > 1 ? FirewallLabels.get('firewalls') : FirewallLabels.get('firewall')
    }
              <em>${groupIds.join(', ')}</em>.`;
  };

  this.showToolTip = (tagName) => {
    return !!_.get(this, ['securityGroupObjectsKeyedByTag', tagName]);
  };

  return this;
});
