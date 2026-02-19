#!/usr/bin/env bash

function show_usage() {
  echo
  echo "Usage: ./configure_upstream_and_downstream.sh ARGUMENTS"
  echo
  echo "ARGUMENTS:"
  echo "  -h, --help            Show this help usage"
  echo "  -u, --upstream        Location where to create upstream repository"
  echo "  -d, --downstream      Location where to create downstream repository"
  echo
}

function parse_arguments() {
  readonly OPTIONS="$(getopt -o hu:d: --long help,upstream:downstream: -n 'configure_upstream_and_downstream.sh' -- "$@")"
  eval set -- "$OPTIONS"

  HELP=false
  while true; do
    case $1 in
    -h | --help)
      HELP=true
      break
      ;;
    -u | --upstream)
      UPSTREAM_DIR="$2"
      shift 2
      ;;
    -d | --downstream)
      DOWNSTREAM_DIR="$2"
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

  validate_arguments

  echo "Parsed arguments are:"
  echo "  upstream repository: '$UPSTREAM_DIR'"
  echo "  downstream repository: '$DOWNSTREAM_DIR'"
}

function validate_arguments() {
  if [[ -z "$UPSTREAM_DIR" || -z "$DOWNSTREAM_DIR" ]]; then
    echo 2>&1 "Both upstream repository directory and downstream repository directory have to be set"
    show_usage
    exit 1
  fi

  check_directory_exists "$UPSTREAM_DIR" "Upstream"
  check_directory_exists "$DOWNSTREAM_DIR" "Downstream"
}

function check_directory_exists() {
  DIR_LOCATION="$1"
  DIR_NAME=$2

  if [[ ! -d "$DIR_LOCATION" ]]; then
    echo 2>&1 "$DIR_NAME directory at '$DIR_LOCATION' should exist, but it does not (make sure it exists, and re-run the script)"
    exit 1
  fi
}

function check_binary_is_installed() {
  local BINARY=$1
  local CMD="$BINARY --version"
  $CMD >/dev/null 2>&1

  if [[ $? -eq 0 ]]; then
    echo -e "\xE2\x9C\x94 OK ($BINARY is installed)"
  else
    echo -e "\xE2\x9D\x8C NOK ($BINARY has to be present but was not found, exiting)" 2>&1
    exit 1
  fi
}

function check_binaries_are_installed() {
  # Note: feel free to add here your own mvn path
  export PATH="$PATH:~/.sdkman/candidates/maven/current/bin"
  check_binary_is_installed git
  check_binary_is_installed mvn
}

function configure_git() {
  # Only set global values if not set already
  [ -z "$(git config --global init.defaultBranch)" ] && git config --global init.defaultBranch main
  [ -z "$(git config --global user.email)" ] && git config --global user.email "user@redhat.com"
  [ -z "$(git config --global user.name)" ] && git config --global user.name "Name Surname"
}

function configure_upstream() {
  echo "Configuring upstream in the directory '$UPSTREAM_DIR'..."
  pushd "$UPSTREAM_DIR"

  git init
  git remote add origin "file://${UPSTREAM_DIR}"
  mvn archetype:generate \
    -DgroupId=com.example \
    -DartifactId=my-app \
    -DarchetypeArtifactId=maven-archetype-quickstart \
    -DarchetypeVersion=1.5 \
    -DinteractiveMode=false
  git add --all
  git commit -m "initial"

  popd
}

function configure_downstream() {
  echo "Configuring downstream in the directory '$DOWNSTREAM_DIR'..."
  pushd "$DOWNSTREAM_DIR"

  git init
  git remote add upstream "file://${UPSTREAM_DIR}"
  git remote add origin "file://${DOWNSTREAM_DIR}"
  git fetch upstream
  git merge upstream/main

  # create a new branch with new version
  git switch -c "1.1"
  sed -i 's/1.0-SNAPSHOT/1.1-SNAPSHOT/g' my-app/pom.xml
  git add my-app/pom.xml
  git commit -m "upgrade version"
  git switch -

  popd
}

function main() {
  parse_arguments "$@"

  check_binaries_are_installed
  configure_git

  configure_upstream
  configure_downstream
}

main "$@"
