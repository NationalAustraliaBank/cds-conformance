#!/bin/bash

# Submodules directory
rm -fr submodules
mkdir submodules
cd submodules

# Install cds-models
git clone https://github.com/ConsumerDataStandardsAustralia/cds-models
cd cds-models
test TRAVIS_PULL_REQUEST && git checkout origin/$TRAVIS_PULL_REQUEST_BRANCH || git checkout origin/$TRAVIS_BRANCH
mvn clean install
cd ../

# Install cds-codegen
git clone https://github.com/ConsumerDataStandardsAustralia/cds-codegen
cd cds-codegen
test TRAVIS_PULL_REQUEST && git checkout origin/$TRAVIS_PULL_REQUEST_BRANCH || git checkout origin/$TRAVIS_BRANCH
mvn clean install
cd ../

