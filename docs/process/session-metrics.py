#!/usr/bin/env python3
"""Compute time, token, context and cost metrics from Claude Code session logs.

Claude Code stores each session as JSON Lines in
~/.claude/projects/<project>/<session-id>.jsonl. This script reads one or more
of those files, optionally limited to a time window, and prints the metrics
used in docs/process/sdd-openspec-case-study.md.

Usage:
    session-metrics.py SESSION.jsonl [SESSION.jsonl ...]
        [--since 2026-09-27T20:16:00Z] [--until 2026-09-28T00:52:00Z]
        [--break-minutes 15] [--wpm 40] [--json]

Definitions:
    active time   sum of gaps between consecutive messages of at most
                  --break-minutes; longer gaps are breaks
    your turn     agent's last output -> next human message (reading, thinking,
                  typing); after a break, estimated as words / --wpm
    waiting       human message -> agent's last output before the next message
    tokens        from each API response's usage, deduplicated by message id
    context       input + cache writes + cache reads of a single request
    cost          Claude Code's own list-price tracker (cost-state events); it is
                  not limited by --since/--until, because snapshots have no timestamp
"""
import argparse
import collections
import json
import statistics
from datetime import datetime, timezone


def parse_ts(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00'))


def human_text(event):
    """Return the text of a message typed by the human, or None."""
    if event.get('type') != 'user' or event.get('isMeta') or event.get('isSidechain'):
        return None
    content = event.get('message', {}).get('content')
    if isinstance(content, list):
        if any(isinstance(c, dict) and c.get('type') == 'tool_result' for c in content):
            return None
        content = ' '.join(c.get('text', '') for c in content
                           if isinstance(c, dict) and c.get('type') == 'text')
    if not content or not content.strip():
        return None
    if content.lstrip().startswith('<system-reminder>') and '<command-name>' not in content:
        return None
    if '<local-command-stdout>' in content or '<command-name>/exit' in content:
        return None
    return content


def load(paths, since, until):
    rows, costs = [], {}
    for path in paths:
        with open(path) as handle:
            for line in handle:
                try:
                    event = json.loads(line)
                except ValueError:
                    continue
                if event.get('type') == 'cost-state':
                    run = (path, event.get('startTime'))
                    costs[run] = max(costs.get(run, 0), event.get('totalCostUSD', 0))
                    continue
                if not event.get('timestamp'):
                    continue
                t = parse_ts(event['timestamp'])
                if (since and t < since) or (until and t > until):
                    continue
                rows.append((t, event))
    rows.sort(key=lambda r: r[0])
    return rows, costs


def analyse(rows, costs, break_s, wpm):
    events = [t for t, e in rows if e.get('type') in ('user', 'assistant')]
    active = sum((b - a).total_seconds() for a, b in zip(events, events[1:])
                 if (b - a).total_seconds() <= break_s)

    human_s, words_in, prompts, last_agent = 0.0, 0, [], None
    for t, e in rows:
        if e.get('type') == 'assistant' and not e.get('isSidechain'):
            last_agent = t
        text = human_text(e)
        if text is None:
            continue
        words = len(text.split())
        words_in += words
        prompts.append(t)
        if last_agent:
            gap = (t - last_agent).total_seconds()
            human_s += gap if gap <= break_s else words / wpm * 60

    agent_times = [t for t, e in rows if e.get('type') == 'assistant']
    bounds = prompts + [datetime.max.replace(tzinfo=timezone.utc)]
    waiting_s = 0.0
    for start, end in zip(bounds, bounds[1:]):
        inside = [t for t in agent_times if start <= t < end]
        if inside:
            waiting_s += (max(inside) - start).total_seconds()

    usage, tools, words_out = {}, collections.Counter(), 0
    for _, e in rows:
        if e.get('type') != 'assistant':
            continue
        message = e['message']
        for block in message.get('content') or []:
            if block.get('type') == 'tool_use':
                tools[block['name']] += 1
            elif block.get('type') == 'text' and not e.get('isSidechain'):
                words_out += len(block['text'].split())
        if message.get('id') and message.get('usage'):
            usage[message['id']] = (message.get('model') or '', message['usage'])

    tokens, contexts = collections.Counter(), []
    for model, u in usage.values():
        tokens['input'] += u.get('input_tokens', 0)
        tokens['cache_write'] += u.get('cache_creation_input_tokens', 0)
        tokens['cache_read'] += u.get('cache_read_input_tokens', 0)
        tokens['output'] += u.get('output_tokens', 0)
        tokens['thinking'] += (u.get('output_tokens_details') or {}).get('thinking_tokens', 0)
        if 'haiku' not in model:  # background helper calls don't reflect the conversation
            contexts.append(u.get('input_tokens', 0) + u.get('cache_creation_input_tokens', 0)
                            + u.get('cache_read_input_tokens', 0))
    all_input = tokens['input'] + tokens['cache_write'] + tokens['cache_read']

    return {
        'start': rows[0][0].isoformat() if rows else None,
        'end': rows[-1][0].isoformat() if rows else None,
        'wall_clock_min': round((rows[-1][0] - rows[0][0]).total_seconds() / 60, 1) if rows else 0,
        'active_min': round(active / 60, 1),
        'your_turn_min': round(human_s / 60, 1),
        'waiting_min': round(waiting_s / 60, 1),
        'human_messages': len(prompts),
        'words_typed': words_in,
        'words_written_by_agent': words_out,
        'api_calls': len(usage),
        'tokens': dict(tokens),
        'tokens_processed': all_input + tokens['output'],
        'cache_hit_rate': round(tokens['cache_read'] / all_input, 4) if all_input else 0,
        'context_median': statistics.median(contexts) if contexts else 0,
        'context_peak': max(contexts) if contexts else 0,
        'tool_calls': dict(tools.most_common()),
        'cost_usd_list_price': round(sum(costs.values()), 2),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('sessions', nargs='+')
    parser.add_argument('--since', type=parse_ts)
    parser.add_argument('--until', type=parse_ts)
    parser.add_argument('--break-minutes', type=float, default=15)
    parser.add_argument('--wpm', type=float, default=40)
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args()

    rows, costs = load(args.sessions, args.since, args.until)
    result = analyse(rows, costs, args.break_minutes * 60, args.wpm)
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        for key, value in result.items():
            print(f'{key:24} {value}')


if __name__ == '__main__':
    main()
