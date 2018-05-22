import re
from datetime import datetime
from .pull_request_event import GetPullRequest
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

        approved = next((r for r in pull_request.get_reviews() if r.state == 'APPROVED'), None) is not None

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
                'approved': approved,
                'merged': merged
            }
        )

PullRequestClosedEventHandler()
