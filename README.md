# cds-conformance [![Build Status](https://travis-ci.com/ConsumerDataStandardsAustralia/cds-conformance.svg?branch=master)](https://travis-ci.com/ConsumerDataStandardsAustralia/cds-conformance)

### Important Note: 
*It's not official conformance tests for the regime. [ACCC](https://www.accc.gov.au/) will probably provide official conformance tests in future.* 

CDS conformance is provided by CDS engineering team under MIT license and it serves two purposes:

1. It's provided as a basic conformance test against Open Banking API endpoints to the best of CDS engineering team's knowledge. The test can be run with
```mvn verify -DapiBase=http://localhost/cds-au/v1``` You can replace `http://localhost/cds-au/v1` 
with any open banking API endpoint.

2. It serves a library which does payload conformance verification. [cds-java-client-cli](https://github.com/ConsumerDataStandardsAustralia/cds-client-java-cli)
is an example of that.
