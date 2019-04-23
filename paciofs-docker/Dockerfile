FROM ubuntu:18.04
LABEL maintainer="schmidtke@zib.de"

ENV DEBIAN_FRONTEND="noninteractive"

RUN apt-get --quiet update \
  && apt-get --quiet --yes install --no-install-recommends \
  \
  # system dependencies
  curl \
  unzip \
  wget \
  \
  # build dependencies
  autoconf \
  automake \
  build-essential \
  git \
  libboost-all-dev \
  libfuse-dev \
  libtool \
  maven \
  openjdk-11-jdk \
  pkg-config \
  \
  # cleanup
  && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp

# install cmake
ENV CMAKE_VERSION="3.13.4"
RUN wget --output-document cmake.tar.gz --quiet https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz \
  && tar --directory /usr/local --extract --file cmake.tar.gz --strip-components 1 \
  && rm cmake.tar.gz

# install go
ENV GO_VERSION="1.11.5"
RUN wget --output-document go.tar.gz --quiet https://dl.google.com/go/go${GO_VERSION}.linux-amd64.tar.gz \
  && tar --directory /usr/local --extract --file go.tar.gz \
  && rm go.tar.gz
ENV GOPATH="/go"
ENV GOBIN="${GOPATH}/bin"
ENV PATH="/usr/local/go/bin:${GOBIN}:${PATH}"

# install dep
RUN mkdir -p "${GOBIN}"
ENV DEP_RELEASE_TAG="v0.5.0"
RUN curl --silent https://raw.githubusercontent.com/golang/dep/master/install.sh | sh

# install gRPC, protoc headers and libraries
ENV GRPC_VERSION="1.18.0"
ENV PROTOBUF_VERSION="3.6.1"
ENV HAS_SYSTEM_PROTOBUF=false
RUN git clone --branch v$GRPC_VERSION https://github.com/grpc/grpc.git \
  && cd ./grpc && git submodule update --init \
  && cd ./third_party/protobuf && git checkout tags/v${PROTOBUF_VERSION} \
  && cd ../../ && make -j2 && make install \
  && cd ./third_party/protobuf && make install \
  && cd ../../../ && rm -rf ./grpc

# install MultiChain
ENV MULTICHAIN_VERSION="2.0-beta-2"
RUN wget --output-document multichain.tar.gz --quiet https://www.multichain.com/download/multichain-${MULTICHAIN_VERSION}.tar.gz \
  && tar --directory /usr/local --extract --file multichain.tar.gz \
  && rm multichain.tar.gz
ENV MULTICHAIN_HOME="/usr/local/multichain-${MULTICHAIN_VERSION}"

# install PacioFS dependencies
WORKDIR /paciofs
COPY ./pom.xml ./pom.xml
COPY ./paciofs-csi/pom.xml ./paciofs-csi/pom.xml
COPY ./paciofs-fuse/pom.xml ./paciofs-fuse/pom.xml
COPY ./paciofs-kubernetes/pom.xml ./paciofs-kubernetes/pom.xml
COPY ./paciofs-server/pom.xml ./paciofs-server/pom.xml
RUN mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline --define silent=true --define protobuf.version=${PROTOBUF_VERSION} --define grpc.version=${GRPC_VERSION}

# build PacioFS
COPY ./paciofs-csi ./paciofs-csi/
COPY ./paciofs-fuse ./paciofs-fuse/
COPY ./paciofs-kubernetes ./paciofs-kubernetes/
COPY ./paciofs-server ./paciofs-server/
RUN mvn --activate-profiles docker --define protobuf.version=${PROTOBUF_VERSION} --define grpc.version=${GRPC_VERSION} clean install

# run PacioFS
ENTRYPOINT [ "java", "-jar", "/paciofs/paciofs-server/target/paciofs-server-1.0.0-SNAPSHOT-allinone.jar" ]
CMD [ "--config", "/paciofs/paciofs-server/src/test/resources/debug.conf" ]
