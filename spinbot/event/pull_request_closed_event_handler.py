import re
from datetime import datetime

from gh import ParseReleaseBranch, ParseCommitMessage, AddLabel
from .pull_request_event import GetBaseBranch, GetPullRequest, GetRepo
from .handler import Handler

class PullRequestClosedEventHandler(Handler):
    def __init__(self):
        super().__init__()

    def handles(self, event):
        return (event.type == 'PullRequestEvent'
            and event.payload.get('action') == 'closed')

    def handle(self, g, event):
        pull_request = GetPullRequest(g, event)
        if not pull_request:
            log.warn('Unable to determine pull request for {}'.format(event))
            return

        merged = pull_request.merged
        age = pull_request.closed_at - pull_request.created_at
        change_size = pull_request.additions + pull_request.deletions
        files_changed_count = pull_request.changed_files
        comments = pull_request.comments
        parsed_message = ParseCommitMessage(pull_request.title)
        change_type = None
        if parsed_message is not None:
            change_type = parsed_message.get('type')

        approved = next((r for r in pull_request.get_reviews() if r.state == 'APPROVED'), None) is not None

        release_branch = self.label_release(g, event, pull_request)

        self.monitoring_db.write('pull_request_closed',
            {
                'count': 1,
                'age_days': age.days,
                'loc_delta': change_size,
                'files_changed_count': files_changed_count,
                'comments': comments
            },
            tags={
                'repo': event.repo.name,
                'user': event.actor.login,
                'release_branch': release_branch,
                'change_type': change_type,
                'approved': approved,
                'merged': merged
            }
        )

    def label_release(self, g, event, pull_request):
        if not pull_request or not pull_request.merged:
            return None

        base_branch = GetBaseBranch(event)
        release_branch = ParseReleaseBranch(base_branch)
        if release_branch != None:
            return self.target_release(g, pull_request, release_branch)

        repo = GetRepo(event)
        branches = g.get_branches(repo)

        parsed = [ ParseReleaseBranch(b.name) for b in branches if ParseReleaseBranch(b.name) is not None ]
        if len(parsed) == 0:
            self.logging.warn('No release branches in {}'.format(repo))
            return None

        parsed.sort()
        release_branch = parsed[-1]
        release_branch[1] = release_branch[1] + 1
        return self.target_release(g, pull_request, release_branch)

    def target_release(self, g, pull_request, release_branch):
        release_name = '.'.join([ str(v) for v in release_branch ])
        label = 'target-release/{}'.format(release_name)
        AddLabel(g, pull_request, label)

        return release_name

PullRequestClosedEventHandler()
