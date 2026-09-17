#!/usr/bin/env python3
"""Fills the site's {{TOKENS}} and writes the result to a separate directory.

Values come from environment variables (set from repository variables in
.github/workflows/pages.yml), so personal details never live in the repo:

  REPO_URL        e.g. https://github.com/<owner>/Rubify
  DEVELOPER_NAME  shown as developer and copyright holder
  CONTACT_EMAIL   public contact address for the privacy policy

Usage: python3 site/render.py <output-dir>
"""

import html
import os
import pathlib
import re
import shutil
import sys

TOKENS = ("REPO_URL", "DEVELOPER_NAME", "CONTACT_EMAIL")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    source = pathlib.Path(__file__).resolve().parent
    output = pathlib.Path(sys.argv[1])

    values = {name: os.environ.get(name, "").strip() for name in TOKENS}
    missing = [name for name, value in values.items() if not value]
    if missing:
        sys.exit(f"Missing values for: {', '.join(missing)}")

    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(source, output, ignore=shutil.ignore_patterns("render.py", "__pycache__"))

    for page in output.rglob("*.html"):
        text = page.read_text(encoding="utf-8")
        for name, value in values.items():
            text = text.replace("{{" + name + "}}", html.escape(value, quote=True))
        leftover = re.findall(r"\{\{[A-Z_]+\}\}", text)
        if leftover:
            sys.exit(f"{page}: unfilled tokens {sorted(set(leftover))}")
        page.write_text(text, encoding="utf-8")
    print(f"Rendered site to {output}")


if __name__ == "__main__":
    main()
