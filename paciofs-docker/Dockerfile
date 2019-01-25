FROM ubuntu:18.04
LABEL maintainer="schmidtke@zib.de"

ENV DEBIAN_FRONTEND="noninteractive"

# update system
RUN apt-get --quiet update \
  && apt-get --quiet --yes install --no-install-recommends apt-utils \
  && apt-get --quiet --yes upgrade

# system dependencies
RUN apt-get --quiet --yes install \
  curl \
  unzip \
  wget

# build dependencies
RUN apt-get --quiet --yes install \
  build-essential \
  cmake \
  git \
  libfuse-dev \
  maven \
  openjdk-8-jdk

WORKDIR /tmp

# install go
ENV GO_VERSION="1.11.4"
RUN wget --output-document go.tar.gz --quiet https://dl.google.com/go/go${GO_VERSION}.linux-amd64.tar.gz \
  && tar --directory /usr/local --extract --file go.tar.gz \
  && rm go.tar.gz
ENV GOPATH="/go"
ENV GOBIN="${GOPATH}/bin"
ENV PATH="${PATH}:/usr/local/go/bin:${GOBIN}"

# install dep
RUN mkdir -p "${GOBIN}"
ENV DEP_RELEASE_TAG="v0.5.0"
RUN curl --silent https://raw.githubusercontent.com/golang/dep/master/install.sh | sh

# install protoc and headers
ENV PROTOBUF_VERSION="3.6.1"
RUN wget --output-document protobuf.tar.gz --quiet https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOBUF_VERSION}/protobuf-all-${PROTOBUF_VERSION}.tar.gz \
  && tar --directory /usr/local --extract --file protobuf.tar.gz \
  && rm protobuf.tar.gz
ENV PROTOBUF_PATH="/usr/local/protobuf-${PROTOBUF_VERSION}"
RUN wget --output-document protoc.zip --quiet https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOBUF_VERSION}/protoc-${PROTOBUF_VERSION}-linux-x86_64.zip \
  && unzip protoc.zip -d ${PROTOBUF_PATH} bin/protoc \
  && rm protoc.zip

# install MultiChain
ENV MULTICHAIN_VERSION="2.0-beta-1"
RUN wget --output-document multichain.tar.gz --quiet https://www.multichain.com/download/multichain-${MULTICHAIN_VERSION}.tar.gz \
  && tar --directory /usr/local --extract --file multichain.tar.gz \
  && rm multichain.tar.gz
ENV MULTICHAIN_PATH="/usr/local/multichain-${MULTICHAIN_VERSION}"

# install specific version of commons-exec
ENV COMMONS_EXEC_VERSION="42eb77c321f7dc17aaff45e45ac1179256517005"
RUN wget --output-document commons-exec.zip --quiet https://github.com/apache/commons-exec/archive/${COMMONS_EXEC_VERSION}.zip \
   && unzip commons-exec.zip \
   && rm commons-exec.zip
RUN cd commons-exec-${COMMONS_EXEC_VERSION} \
  && mvn install --define maven.compiler.source=1.8 --define maven.compiler.target=1.8 --define skipTests=true --define maven.javadoc.skip=true --batch-mode \
  && cd .. \
  && rm -rf commons-exec-${COMMONS_EXEC_VERSION}

# install PacioFS dependencies
WORKDIR /paciofs
COPY ./pom.xml ./pom.xml
COPY ./paciofs-csi/pom.xml ./paciofs-csi/pom.xml
COPY ./paciofs-fuse/pom.xml ./paciofs-fuse/pom.xml
COPY ./paciofs-kubernetes/pom.xml ./paciofs-kubernetes/pom.xml
COPY ./paciofs-server/pom.xml ./paciofs-server/pom.xml
RUN mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline --define silent=true

# build PacioFS
COPY ./paciofs-csi ./paciofs-csi/
COPY ./paciofs-fuse ./paciofs-fuse/
COPY ./paciofs-kubernetes ./paciofs-kubernetes/
COPY ./paciofs-server ./paciofs-server/
RUN mvn --activate-profiles docker --define protobuf.protoc=${PROTOBUF_PATH}/bin/protoc --define protobuf.include.dirs=${PROTOBUF_PATH}/src clean install

# run PacioFS
CMD [ "/bin/sh", "-c", \
  "java -jar ./paciofs-server/target/paciofs-server-1.0.0-SNAPSHOT-allinone.jar" ]
