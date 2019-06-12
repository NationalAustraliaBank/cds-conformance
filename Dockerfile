FROM openjdk:8-jdk-stretch
MAINTAINER Data61 <cdr-data61@csiro.au>

EXPOSE 8000/tcp

ADD . /opt/cds-conformance

WORKDIR /opt/cds-conformance

RUN apt-get update
RUN apt-get -y install maven dumb-init

RUN mvn -q -Ddockerfile.skip=true -DskipTests=true clean package
RUN mvn -q -Ddockerfile.skip=true verify || true

RUN chmod +x /opt/cds-conformance/support/docker-init.sh

ENTRYPOINT ["/opt/cds-conformance/support/docker-init.sh"]
