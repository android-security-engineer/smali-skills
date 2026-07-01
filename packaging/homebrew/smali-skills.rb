# Homebrew formula for smali-skills.
#
# This lives in a Homebrew "tap" — a repo named `homebrew-tap` under the same
# org. To publish:
#
#   1. Create repo  android-security-engineer/homebrew-tap
#   2. Copy this file to  Formula/smali-skills.rb  in that repo
#   3. Fill in `version`, `url`, and `sha256` (the release workflow's
#      `update-homebrew` job does this automatically on each v* tag)
#
# Users then install with:
#
#   brew tap android-security-engineer/tap
#   brew install smali-skills
#
# The formula downloads the released fat jars (no compilation needed) and
# installs `smali` / `baksmali` launcher scripts backed by the system JRE.
class SmaliSkills < Formula
  desc "AI-Agent-oriented smali/baksmali: dex assembler/disassembler + query layer"
  homepage "https://github.com/android-security-engineer/smali-skills"
  version "2.5.2"
  license "BSD-3-Clause"

  # Two release assets are fetched: smali.jar and baksmali.jar. Homebrew's
  # single-`url` model doesn't fit two files well, so we point `url` at the
  # baksmali jar and pull smali.jar as a `resource`.
  url "https://github.com/android-security-engineer/smali-skills/releases/download/v#{version}/baksmali.jar"
  sha256 "REPLACE_WITH_BAKSMALI_SHA256"

  resource "smali" do
    url "https://github.com/android-security-engineer/smali-skills/releases/download/v2.5.2/smali.jar"
    sha256 "REPLACE_WITH_SMALI_SHA256"
  end

  depends_on "openjdk"

  def install
    # Install the baksmali fat jar (fetched via `url`).
    libexec.install "baksmali.jar"
    # Install the smali fat jar (fetched via the resource).
    resource("smali").stage do
      libexec.install "smali.jar"
    end

    # Wrapper scripts that invoke the bundled JRE against the fat jars.
    (bin/"baksmali").write <<~SH
      #!/bin/bash
      exec "#{Formula["openjdk"].opt_bin}/java" -jar "#{libexec}/baksmali.jar" "$@"
    SH
    (bin/"smali").write <<~SH
      #!/bin/bash
      exec "#{Formula["openjdk"].opt_bin}/java" -jar "#{libexec}/smali.jar" "$@"
    SH
  end

  test do
    # `--help` exits non-zero on some jcommander CLIs; just assert it runs and
    # prints the expected banner text.
    assert_match "baksmali", shell_output("#{bin}/baksmali --help 2>&1", 0)
    assert_match "smali", shell_output("#{bin}/smali --help 2>&1", 0)
  end
end
