#!/usr/bin/env bash
set -euo pipefail
project="$(cd "$(dirname "$0")/.." && pwd)"
build="$(mktemp -d)"
trap 'rm -rf "$build"' EXIT
javac -encoding UTF-8 -d "$build" "$project"/app/src/main/java/vn/viettel/caobang/kpitammi/v2/ReportRecipients.java "$project"/app/src/main/java/vn/viettel/caobang/kpitammi/v2/ReportShareBundle.java "$project"/tests/ManualReportChecks.java
java -cp "$build" ManualReportChecks


javac -encoding UTF-8 -d "$build" "$project"/app/src/main/java/vn/viettel/caobang/kpitammi/v2/AutoRouteMatcher.java "$project"/tests/AutoRoutingChecks.java
java -cp "$build" AutoRoutingChecks
