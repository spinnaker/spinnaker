#!/usr/bin/env python3

import github
import logging
import monitoring
import heapq
import os

class Client(object):
    def __init__(self, config):
        token = self.get_token(config)
        self.g = github.Github(token)
        self.repos = config['repos']
        self.monitoring_db = monitoring.GetDatabase('spinbot')
        self.logging = logging.getLogger('github_client_wrapper')

    def get_token(self, config):
        token_path = config.get('token_path')
        if token_path is None:
            return config.get('token')

        with open(os.path.expanduser(token_path), 'r') as f:
            return f.read().strip()

    def rate_limit(self):
        ret = self.g.get_rate_limit()
        self.monitoring_db.write('rate_limit_remaining', { 'value': ret.rate.remaining })
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

    def issues(self):
        for r in self.repos:
            issues = 0
            self.logging.info('Reading issues from {}'.format(r))
            for i in self.g.get_repo(r).get_issues():
                issues += 1
                yield i

            self.monitoring_db.write('issues_count', { 'value': issues }, tags={ 'repo': r })

        raise StopIteration

    def events_since(self, date):
        return heapq.merge(
                *[ reversed(list(self._events_since_repo_iter(date, r))) for r in self.repos ],
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
        raise StopIteration

    def get_pull_request(self, repo, num):
        return self.g.get_repo(repo).get_pull(num)

    def get_issue(self, repo, num):
        return self.g.get_repo(repo).get_issue(num)
