cmake_minimum_required (VERSION 3.12)
project (paciofs_fuse LANGUAGES CXX VERSION 1.0.0)

set (CMAKE_CXX_STANDARD 14)
set (CMAKE_CXX_STANDARD_REQUIRED ON)
set (CMAKE_CXX_EXTENSIONS OFF)

list (APPEND CMAKE_MODULE_PATH "${PROJECT_SOURCE_DIR}/cmake/modules")

find_package (Boost ${BOOST_MIN_VERSION} REQUIRED COMPONENTS
  log
  log_setup
  program_options
)

find_package (FUSE REQUIRED)

find_library (gRPC_LIBRARIES grpc++ "${GRPC_LIBRARY_DIRS}")
find_library (Protobuf_LIBRARIES protobuf "${PROTOBUF_LIBRARY_DIRS}")

set (GENERATED_GRPC_DIR "${PROJECT_SOURCE_DIR}/target/generated-sources/grpc")
set (GENERATED_PROTOBUF_DIR "${PROJECT_SOURCE_DIR}/target/generated-sources/protobuf")

# GLOB for sources is not recommended, however we make an exception for generated sources
# https://cmake.org/cmake/help/v3.12/command/file.html#glob-recurse
file (GLOB_RECURSE GRPC_SOURCES CONFIGURE_DEPENDS "${GENERATED_GRPC_DIR}/*.cc")
file (GLOB_RECURSE PROTOBUF_SOURCES CONFIGURE_DEPENDS "${GENERATED_PROTOBUF_DIR}/*.cc")

add_executable (
  mount.paciofs
  "${PROJECT_SOURCE_DIR}/src/logging.cpp"
  "${PROJECT_SOURCE_DIR}/src/mount_options.cpp"
  "${PROJECT_SOURCE_DIR}/src/mount_paciofs.cpp"
  "${PROJECT_SOURCE_DIR}/src/options.cpp"
  "${PROJECT_SOURCE_DIR}/src/posix_io_rpc_client.cpp"
  ${GRPC_SOURCES}
  ${PROTOBUF_SOURCES}
)

target_compile_definitions (mount.paciofs PRIVATE PACIOFS_VERSION="${PROJECT_VERSION}")
target_compile_definitions (mount.paciofs PRIVATE FUSE_USE_VERSION=${FUSE_VERSION})
target_compile_definitions (mount.paciofs PRIVATE BOOST_ALL_DYN_LINK)
target_compile_options (mount.paciofs PRIVATE -Wall)

target_include_directories (
  mount.paciofs
  PRIVATE
  "${PROJECT_SOURCE_DIR}/include"
  "${GENERATED_GRPC_DIR}"
  "${GENERATED_PROTOBUF_DIR}"
  "${Boost_INCLUDE_DIRS}"
)

# prepend include directories to search path to avoid accidentally using the system headers
target_include_directories (
  mount.paciofs
  BEFORE PRIVATE
  "${FUSE_INCLUDE_DIRS}"
  "${GRPC_INCLUDE_DIRS}"
  "${PROTOBUF_INCLUDE_DIRS}"
)

target_link_libraries (
  mount.paciofs
  "${FUSE_LIBRARIES}"
  "${gRPC_LIBRARIES}"
  "${Protobuf_LIBRARIES}"
  Boost::log
  Boost::log_setup
  Boost::program_options
)
