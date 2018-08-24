import argparse
import os
import yaml
import event
import policy

def parse_args():
    parser = argparse.ArgumentParser(description='Spinbot CLI options')
    event.AddArgs(parser)
    policy.AddArgs(parser)
    return parser.parse_args()

def merge_ctx_args(ctx, args):
    kwargs = args._get_kwargs()
    for arg in kwargs:
        merge_single_arg(ctx, arg)

def merge_single_arg(ctx, arg):
    val = arg[1]
    if val is None:
        return

    key = arg[0].split('.')
    merge_single_arg_key(ctx, key, val)

def merge_single_arg_key(ctx, key, val):
    if len(key) == 0:
        return

    k = key.pop(0)
    if len(key) == 0:
        ctx[k] = val
    else:
        merge_single_arg_key(ctx.get(k, {}), key, val)

def GetCtx():
    ctx = {}
    with open(os.path.expanduser('~/.spinbot/config'), 'r') as f:
        ctx = yaml.safe_load(f)

    args = parse_args()
    merge_ctx_args(ctx, args)

    return ctx
