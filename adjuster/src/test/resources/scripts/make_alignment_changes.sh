#!/usr/bin/env bash

readonly DEFAULT_REPOSITORY_DIR="/tmp/adjust"

function show_usage() {
  echo
  echo "Usage: ./make_alignment_changes.sh ARGUMENTS"
  echo
  echo "ARGUMENTS:"
  echo "  -h, --help                Show this help usage"
  echo "  -d, --repository-dir      Location of the (git) repository to align. Defaults to '$DEFAULT_REPOSITORY_DIR'."
  echo
}

function parse_options() {
  readonly OPTIONS="$(getopt -o hd: --long help,repository-dir: -n 'make_alignment_changes.sh' -- "$@")"
  eval set -- "$OPTIONS"

  HELP=false
  REPOSITORY_DIR="$DEFAULT_REPOSITORY_DIR"
  while true; do
    case $1 in
    -h | --help)
      HELP=true
      break
      ;;
    -d | --repository-dir)
      REPOSITORY_DIR="$2"
      shift 2
      ;;
    --)
      shift
      break
      ;;
    *)
      echo 2>&1 "Invalid option ($1) given"
      show_usage
      exit 1
      ;;
    esac
  done

  if [ "$HELP" = true ]; then
    show_usage
    exit 0
  fi

  echo "Parsed arguments are:"
  echo "  repository directory: '$REPOSITORY_DIR'"

  validate_arguments
}

function validate_arguments() {
  if [[ ! -d "$REPOSITORY_DIR" ]]; then
    echo 2>&1 "Directory at '$REPOSITORY_DIR' should exist, but it does not (make sure it exists, and re-run the script)"
    exit 1
  fi
}

function make_alignment_changes() {
  pushd "$REPOSITORY_DIR"
  sed -i 's/1.0-SNAPSHOT/1.0-aligned-00042/g' my-app/pom.xml
  popd
}

function main() {
  parse_options "$@"

  make_alignment_changes
}

main "$@"
