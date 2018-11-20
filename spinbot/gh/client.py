#!/usr/bin/env python3

import github
import logging
import monitoring
import heapq
import os

from .repo import CherryPick
from .util import ReleaseBranch

class Client(object):
    def __init__(self, config):
        self.token = self.get_token(config)
        self.username = self.get_username(config)
        self.g = github.Github(self.token)
        self._repos = config['repos']
        self.monitoring_db = monitoring.GetDatabase('spinbot')
        self.logging = logging.getLogger('github_client_wrapper')

    def get_username(self, config):
        return config.get('username', 'spinnakerbot')

    def get_token(self, config):
        token_path = config.get('token_path')
        if token_path is None:
            return config.get('token')

        with open(os.path.expanduser(token_path), 'r') as f:
            return f.read().strip()

    def cherry_pick(self, repo, release, commit):
        branch = CherryPick(repo=repo, release=release, commit=commit, username=self.username, password=self.token)
        r = self.g.get_repo(repo)
        c = r.get_commit(commit)
        og_message = c.commit.message
        title = og_message.split('\n')[0]
        body = '\n'.join(og_message.split('\n')[1:])
        message = '{body}\n\n> Automated cherry pick of {commit} into {release}'.format(
                body=body,
                commit=commit, 
                release=release, 
        )

        return r.create_pull(
                title=title, 
                base=ReleaseBranch(release), 
                head=branch,
                body=message,
                maintainer_can_modify=True
        )

    def rate_limit(self):
        ret = self.g.get_rate_limit()
        self.monitoring_db.write('rate_limit_remaining', { 'value': ret.core.remaining })
        return ret

    def get_label(self, repo, name, create=True):
        repo = self.g.get_repo(repo)
        label = None
        try:
            label = repo.get_label(name)
        except:
            pass

        if label is None and create:
            label = repo.create_label(name, '000000')

        return label

    def repos(self):
        for r in self._repos:
            yield self.g.get_repo(r)

    def pull_requests(self):
        for r in self._repos:
            pulls = 0
            self.logging.info('Reading pull requests from {}'.format(r))
            for i in self.g.get_repo(r).get_pulls():
                pulls += 1
                yield i

            self.monitoring_db.write('pull_requests_count', { 'value': pulls }, tags={ 'repo': r })

    def issues(self):
        for r in self._repos:
            issues = 0
            self.logging.info('Reading issues from {}'.format(r))
            for i in self.g.get_repo(r).get_issues():
                issues += 1
                yield i

            self.monitoring_db.write('issues_count', { 'value': issues }, tags={ 'repo': r })

    def events_since(self, date):
        return heapq.merge(
                *[ reversed(list(self._events_since_repo_iter(date, r))) for r in self._repos ],
                key=lambda e: e.created_at
        )

    def _events_since_repo_iter(self, date, repo):
        events = 0
        self.logging.info('Reading events from {}'.format(repo))
        for e in self.g.get_repo(repo).get_events():
            if e.created_at <= date:
                break
            else:
                events += 1
                yield e

        self.monitoring_db.write('events_count', { 'value': events }, tags={ 'repo': repo })

    def get_branches(self, repo):
        return self.g.get_repo(repo).get_branches()

    def get_pull_request(self, repo, num):
        return self.g.get_repo(repo).get_pull(num)

    def get_issue(self, repo, num):
        return self.g.get_repo(repo).get_issue(num)
