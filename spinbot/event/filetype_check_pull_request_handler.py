from .handler import Handler
from .pull_request_event import GetBaseBranch, GetPullRequest, GetTitle, GetRepo
from gh import ReleaseBranchFor, ParseCommitMessage

format_message = ('We prefer that non-test backend code be written in Java or Kotlin, rather ' +
        'than Groovy. The following files have been added and written in Groovy:\n\n' +
        '{}\n\n' + 
        'See our server-side [commit conventions here](https://www.spinnaker.io/community/contributing/back-end-code/#choice-of-language).')

class FiletypeCheckPullRequestHandler(Handler):
    def __init__(self):
        super().__init__()
        self.omit_repos = self.config.get('omit_repos', [])

    def handles(self, event):
        return (event.type == 'PullRequestEvent'
            and event.payload.get('action') == 'opened')

    def handle(self, g, event):
        repo = GetRepo(event)
        if repo in self.omit_repos:
            self.logging.info('Skipping {} because it\'s in omitted repo {}'.format(event, repo))
            return

        pull_request = GetPullRequest(g, event)
        if pull_request is None:
            self.logging.warn('Unable to determine PR that created {}'.format(event))
            return

        files = pull_request.get_files()
        bad_files = []

        for f in files:
            if not f.status == 'added':
                continue
            if not f.filename.endswith('.groovy'):
                continue
            if 'src/test' in f.filename:
                continue
            
            bad_files.append(f)

        if len(bad_files) > 0:
            pull_request.create_issue_comment(format_message.format(
                '\n\n'.join(map(lambda f: '* {}'.format(f.filename), bad_files))))

FiletypeCheckPullRequestHandler()
