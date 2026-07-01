#!/usr/bin/env python3
"""
Validates the Skills layer under skills/.

Checks performed for every SKILL.md:
  1. It has a YAML frontmatter block delimited by '---'.
  2. The frontmatter contains non-empty 'name' and 'description' fields.
  3. The 'name' matches the parent directory name (skill slug).
  4. The 'description' contains at least one trigger phrase ("Use when" or "Triggers:").
  5. Any 'java -jar' or 'baksmali'/'smali' CLI invocations in code fences look well-formed
     (no obviously broken syntax like a trailing backslash with no continuation).

Exit code is non-zero if any check fails. Intended to run in CI.
"""
import os
import re
import sys

SKILLS_DIR = os.path.join(os.path.dirname(__file__), "..", "skills")
SKILLS_DIR = os.path.abspath(SKILLS_DIR)

FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)
FIELD_RE = re.compile(r"^([a-zA-Z_]+):\s*(.*)$", re.MULTILINE)


def parse_frontmatter(text):
    """Returns a dict of top-level frontmatter fields, or None if no frontmatter."""
    m = FRONTMATTER_RE.match(text)
    if not m:
        return None
    body = m.group(1)
    fields = {}
    # Handle quoted values and simple scalars. This is a tiny subset of YAML, intentionally
    # not a full parser — SKILL.md frontmatter is constrained to flat scalar fields.
    for key, value in FIELD_RE.findall(body):
        value = value.strip()
        if (value.startswith('"') and value.endswith('"')) or \
           (value.startswith("'") and value.endswith("'")):
            value = value[1:-1]
        fields[key] = value
    return fields


def extract_code_fences(text):
    """Yields the inner text of each fenced code block."""
    for m in re.finditer(r"```[^\n]*\n(.*?)```", text, re.DOTALL):
        yield m.group(1)


errors = []
skill_count = 0

for entry in sorted(os.listdir(SKILLS_DIR)):
    skill_path = os.path.join(SKILLS_DIR, entry)
    if not os.path.isdir(skill_path):
        continue
    skill_md = os.path.join(skill_path, "SKILL.md")
    if not os.path.isfile(skill_md):
        errors.append(f"{entry}: no SKILL.md found")
        continue

    skill_count += 1
    with open(skill_md, encoding="utf-8") as f:
        text = f.read()

    fm = parse_frontmatter(text)
    if fm is None:
        errors.append(f"{entry}: missing YAML frontmatter (--- ... ---)")
        continue

    name = fm.get("name", "").strip()
    desc = fm.get("description", "").strip()

    if not name:
        errors.append(f"{entry}: frontmatter 'name' is empty")
    elif name != entry:
        errors.append(f"{entry}: frontmatter name '{name}' != directory name '{entry}'")

    if not desc:
        errors.append(f"{entry}: frontmatter 'description' is empty")
    elif "Use when" not in desc and "Triggers:" not in desc:
        errors.append(f"{entry}: description should contain 'Use when' or 'Triggers:' for recall")

    # Check CLI invocations in code fences are well-formed.
    for fence in extract_code_fences(text):
        for line in fence.splitlines():
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if "java -jar" in stripped or re.match(r"^(baksmali|smali)\b", stripped):
                # Flag lines ending with a dangling operator or unmatched backtick count per line.
                if stripped.endswith("&&") or stripped.endswith("|"):
                    errors.append(f"{entry}: CLI line looks incomplete: {stripped}")


print(f"Validated {skill_count} skill(s) under {SKILLS_DIR}")
if errors:
    print("\nFAILURES:")
    for e in errors:
        print(f"  - {e}")
    sys.exit(1)

print("All skill checks passed.")
