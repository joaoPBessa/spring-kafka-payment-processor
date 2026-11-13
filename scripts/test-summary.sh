#!/usr/bin/env bash
# Prints the test totals of every module as a markdown table, read from the
# surefire reports left by `mvn verify`. Used by the CI job summary; also runs locally.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

attribute() {
  local name="$1" tag="$2"
  sed -n "s/.* ${name}=\"\([0-9]*\)\".*/\1/p" <<<"${tag}"
}

module_totals() {
  local reports="$1" tests=0 failures=0 errors=0 skipped=0 tag
  while IFS= read -r tag; do
    tests=$((tests + $(attribute tests "${tag}")))
    failures=$((failures + $(attribute failures "${tag}")))
    errors=$((errors + $(attribute errors "${tag}")))
    skipped=$((skipped + $(attribute skipped "${tag}")))
  done < <(grep -h -m1 '<testsuite ' "${reports}"/TEST-*.xml)
  echo "${tests} ${failures} ${errors} ${skipped}"
}

echo "## Test results"
echo
echo "| Module | Tests | Failures | Errors | Skipped |"
echo "|---|---:|---:|---:|---:|"

total_tests=0 total_failures=0 total_errors=0 total_skipped=0 modules=0
for reports in "${root}"/*/target/surefire-reports; do
  [ -d "${reports}" ] || continue
  compgen -G "${reports}/TEST-*.xml" >/dev/null || continue
  module="$(basename "$(dirname "$(dirname "${reports}")")")"
  read -r tests failures errors skipped <<<"$(module_totals "${reports}")"
  echo "| ${module} | ${tests} | ${failures} | ${errors} | ${skipped} |"
  total_tests=$((total_tests + tests))
  total_failures=$((total_failures + failures))
  total_errors=$((total_errors + errors))
  total_skipped=$((total_skipped + skipped))
  modules=$((modules + 1))
done

if [ "${modules}" -eq 0 ]; then
  echo "| _no surefire reports found_ | | | | |"
else
  echo "| **Total** | **${total_tests}** | **${total_failures}** | **${total_errors}** | **${total_skipped}** |"
fi
