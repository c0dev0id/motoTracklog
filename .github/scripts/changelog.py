"""Release notes from CHANGELOG.md for the Release workflow.

  changelog.py release VERSION DATE
      Moves the [Unreleased] entries of CHANGELOG.md under a "VERSION - DATE" heading and
      prints them.
  changelog.py play-notes URL
      Reads release notes on stdin and prints them as Google Play "What's new" text.
"""
import re
import sys

UNRELEASED = "## [Unreleased]"
PLAY_LIMIT = 500


def split_release(changelog, version, date):
    """Returns the changelog with its unreleased entries under a version heading, and the entries."""
    start = changelog.index(UNRELEASED) + len(UNRELEASED)
    end = changelog.find("\n## [", start)
    if end == -1:
        end = len(changelog)
    notes = changelog[start:end].strip()
    if not notes:
        raise SystemExit("CHANGELOG.md has nothing under [Unreleased]")
    return f"{changelog[:start]}\n\n## [{version}] - {date}\n\n{notes}\n{changelog[end:]}", notes


def entries(notes):
    """Yields (heading, entry) for each list entry, with wrapped lines joined."""
    heading, entry = None, None
    for line in notes.splitlines():
        if line.startswith("### "):
            if entry:
                yield heading, entry
            heading, entry = line[4:].strip(), None
        elif line.startswith("- "):
            if entry:
                yield heading, entry
            entry = line[2:].strip()
        elif line.strip() and entry is not None:
            entry += " " + line.strip()
    if entry:
        yield heading, entry


def plain(text):
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"`([^`]*)`", r"\1", text)
    return re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)


def play_notes(notes, url):
    """Plain-text notes: leading whole entries that fit within PLAY_LIMIT, then a link to url."""
    footer = f"Full release notes: {url}"
    lines, last_heading = [], None
    for heading, entry in entries(notes):
        added = [heading] if heading and heading != last_heading else []
        added.append("• " + plain(entry))
        if len("\n".join(lines + added + ["", footer])) > PLAY_LIMIT:
            break
        lines += added
        last_heading = heading
    return "\n".join(lines + ["", footer]) if lines else footer


def main(argv):
    match argv:
        case ["release", version, date]:
            with open("CHANGELOG.md", encoding="utf-8") as f:
                updated, notes = split_release(f.read(), version, date)
            with open("CHANGELOG.md", "w", encoding="utf-8") as f:
                f.write(updated)
            print(notes)
        case ["play-notes", url]:
            sys.stdout.write(play_notes(sys.stdin.read(), url))
        case _:
            raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
