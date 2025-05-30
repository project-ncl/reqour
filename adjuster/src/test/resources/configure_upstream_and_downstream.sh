#!/usr/bin/env bash

readonly UPSTREAM_DIR="${1%/}/upstream"
readonly DOWNSTREAM_DIR="${1%/}/downstream"

function check_binary_is_installed() {
  local BINARY=$1
  local CMD="$BINARY --version"
  $CMD >/dev/null 2>&1

  if [[ $? -eq 0 ]]; then
    echo -e "\xE2\x9C\x94 OK ($BINARY is installed)"
  else
    echo -e 2>&1 "\xE2\x9D\x8C NOK ($BINARY has to be present but was not found, exiting)"
    exit 1
  fi
}

function check_binaries_are_installed() {
  # Note: feel free to add here your own mvn path
  export PATH="$PATH:~/.sdkman/candidates/maven/current/bin"
  check_binary_is_installed git
  check_binary_is_installed mvn
}

function configure_upstream() {
  echo "Configuring upstream in the directory '$UPSTREAM_DIR'..."
  mkdir "$UPSTREAM_DIR"
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
  mkdir "$DOWNSTREAM_DIR"
  pushd "$DOWNSTREAM_DIR"

  git init
  git remote add downstream "file://${DOWNSTREAM_DIR}"
  git remote add upstream "file://${UPSTREAM_DIR}"
  git fetch upstream
  git merge upstream/main

  # create a new branch with new version
  sed -i 's/1.0-SNAPSHOT/1.1-SNAPSHOT/g' my-app/pom.xml
  git switch -c "1.1"
  git add my-app/pom.xml
  git commit -m "upgrade version"
  git switch -

  popd
}

if [[ $# -ne 1 ]]; then
  echo 2>&1 "Expecting exactly one argument (working directory), exiting"
  exit 1
fi


check_binaries_are_installed
configure_upstream
configure_downstream
