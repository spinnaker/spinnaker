import github
import logging

logging = logging.getLogger('github_util')

def IssueRepo(issue):
    return '/'.join(issue.url.split('/')[-4:-2])

def PullRequestRepo(issue):
    return '/'.join(issue.url.split('/')[-4:-2])

def HasLabel(issue, name):
    label = next((l for l in issue.get_labels() if l.name == name), None)
    return label is not None

def ReleaseBranch(release):
    return 'release-{}.x'.format(release)

def RemoveLabel(gh, issue, name, create=True):
    if not HasLabel(issue, name):
        issue.create_comment(
            '"{}" has not been applied, and cannot be removed.'.format(name)
        )
        return

    label = gh.get_label(IssueRepo(issue), name, create=create)
    if label is None:
        logging.warn(
            'Label {} exists on the issue but was not found'.format(name)
        )
        return

    issue.remove_from_labels(label)

def AddLabel(gh, issue, name, create=True):
    if HasLabel(issue, name):
        return

    label = gh.get_label(IssueRepo(issue), name, create=create)
    if label is None:
        issue.create_comment(
            'Sorry! "{}" is not a label yet, and I don\'t create '.format(name)
            + 'labels to avoid spam.'
        )
        return
    issue.add_to_labels(label)

def ObjectType(o):
    if isinstance(o, github.Issue.Issue) and o.html_url.split('/')[-2] == 'issues':
        return 'issue'
    if isinstance(o, github.PullRequest.PullRequest):
        return 'pull_request'
    elif isinstance(o, github.Repository.Repository):
        return 'repository'
    else:
        return None
