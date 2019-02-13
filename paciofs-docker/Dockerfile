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
  autoconf \
  build-essential \
  git \
  libboost-all-dev \
  libfuse-dev \
  libtool \
  maven \
  openjdk-11-jdk \
  pkg-config

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
ENV MULTICHAIN_PATH="/usr/local/multichain-${MULTICHAIN_VERSION}"

# install sbt for akka-http
ENV SBT_VERSION="1.2.8"
RUN wget --output-document sbt.tgz --quiet https://piccolo.link/sbt-${SBT_VERSION}.tgz \
  && tar --directory /usr/local --extract --file sbt.tgz \
  && rm sbt.tgz
ENV PATH="/usr/local/sbt/bin:${PATH}"

# install custom version of akka-http
ENV AKKA_HTTP_VERSION="2443640a8c6e8883280b1c51c923bc98145b8c4a"
RUN wget --output-document akka-http.zip --quiet https://github.com/akka/akka-http/archive/${AKKA_HTTP_VERSION}.zip \
  && unzip akka-http.zip \
  && rm akka-http.zip \
  && cd akka-http-${AKKA_HTTP_VERSION} \
  && sbt -Dakka.scaladoc.diagrams=false publishM2 \
  && cd ../ \
  && rm -rf akka-http-${AKKA_HTTP_VERSION}
ENV AKKA_HTTP_VERSION="$(grep -E -o 'HEAD\+[[:digit:]]{8}-[[:digit:]]{4}' $(find . -name 'Version.scala'))"

# install PacioFS dependencies
WORKDIR /paciofs
COPY ./pom.xml ./pom.xml
COPY ./paciofs-csi/pom.xml ./paciofs-csi/pom.xml
COPY ./paciofs-fuse/pom.xml ./paciofs-fuse/pom.xml
COPY ./paciofs-kubernetes/pom.xml ./paciofs-kubernetes/pom.xml
COPY ./paciofs-server/pom.xml ./paciofs-server/pom.xml
RUN mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline --define silent=true --define akka.http.version=${AKKA_HTTP_VERSION} --define protobuf.version=${PROTOBUF_VERSION} --define grpc.version=${GRPC_VERSION}

# build PacioFS
COPY ./paciofs-csi ./paciofs-csi/
COPY ./paciofs-fuse ./paciofs-fuse/
COPY ./paciofs-kubernetes ./paciofs-kubernetes/
COPY ./paciofs-server ./paciofs-server/
RUN mvn --activate-profiles docker --define akka.http.version=${AKKA_HTTP_VERSION} --define protobuf.version=${PROTOBUF_VERSION} --define grpc.version=${GRPC_VERSION} clean install

# run PacioFS
CMD [ "/bin/sh", "-c", \
  "java -jar ./paciofs-server/target/paciofs-server-1.0.0-SNAPSHOT-allinone.jar" ]
