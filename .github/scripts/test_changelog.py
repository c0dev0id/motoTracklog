import unittest

from changelog import PLAY_LIMIT, play_notes, split_release

URL = "https://example.org/releases/tag/v1.0.1"

CHANGELOG = """# Changelog

Intro.

## [Unreleased]

### Added
- **Parking.** A PIN keypad
  that pins the screen.

## [1.0.0] - 2026-10-01

### Added
- First release.
"""


class SplitReleaseTest(unittest.TestCase):

    def test_moves_unreleased_entries_under_the_version(self):
        updated, notes = split_release(CHANGELOG, "1.0.1", "2026-10-19")
        self.assertEqual(
            "### Added\n- **Parking.** A PIN keypad\n  that pins the screen.", notes)
        self.assertEqual("""# Changelog

Intro.

## [Unreleased]

## [1.0.1] - 2026-10-19

### Added
- **Parking.** A PIN keypad
  that pins the screen.

## [1.0.0] - 2026-10-01

### Added
- First release.
""", updated)

    def test_first_release_has_no_older_version_below(self):
        changelog = "# Changelog\n\n## [Unreleased]\n\n### Added\n- One.\n"
        updated, _ = split_release(changelog, "1.0.0", "2026-10-19")
        self.assertEqual(
            "# Changelog\n\n## [Unreleased]\n\n## [1.0.0] - 2026-10-19\n\n### Added\n- One.\n",
            updated)

    def test_refuses_an_empty_unreleased_section(self):
        with self.assertRaises(SystemExit):
            split_release("## [Unreleased]\n\n## [1.0.0] - 2026-10-01\n- x\n", "1.0.1", "d")


class PlayNotesTest(unittest.TestCase):

    def test_plain_text_with_joined_lines_and_link(self):
        notes = "### Added\n- **Parking.** A `PIN` keypad\n  that [pins](https://x) it."
        self.assertEqual(
            f"Added\n• Parking. A PIN keypad that pins it.\n\nFull release notes: {URL}",
            play_notes(notes, URL))

    def test_stops_at_the_first_entry_that_does_not_fit(self):
        notes = "### Added\n- Short.\n- " + "x" * 450 + "\n- Also short."
        self.assertEqual(
            f"Added\n• Short.\n\nFull release notes: {URL}", play_notes(notes, URL))

    def test_heading_appears_only_with_an_entry_under_it(self):
        notes = "### Added\n- One.\n### Fixed\n- " + "y" * 450
        self.assertNotIn("Fixed", play_notes(notes, URL))

    def test_stays_within_the_limit(self):
        notes = "### Added\n" + "".join(f"- Entry number {i} here.\n" for i in range(100))
        self.assertLessEqual(len(play_notes(notes, URL)), PLAY_LIMIT)

    def test_only_the_link_when_no_entry_fits(self):
        self.assertEqual(
            f"Full release notes: {URL}", play_notes("- " + "z" * 600, URL))


if __name__ == "__main__":
    unittest.main()
