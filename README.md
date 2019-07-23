# cds-conformance [![Build Status](https://travis-ci.com/ConsumerDataStandardsAustralia/cds-conformance.svg?branch=master)](https://travis-ci.com/ConsumerDataStandardsAustralia/cds-conformance)

CDS conformance serves two purposes:

1. It provides a conformance test against Open Banking API endpoints. The test can be run with
```mvn verify -DapiBase=http://localhost/cds-au/v1``` You can replace `http://localhost/cds-au/v1` 
with any open banking API endpoint.

2. It serves a library which does playload conformance verification. [cds-java-client-cli](https://github.com/ConsumerDataStandardsAustralia/cds-client-java-cli)
is an example of that.
