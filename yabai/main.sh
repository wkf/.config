#!/usr/bin/env sh

bb --classpath "$(dirname "$0")" "$(dirname "$0")/main.clj" "$@"
