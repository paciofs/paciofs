cmake_minimum_required (VERSION 3.1)
project (paciofs_fuse LANGUAGES CXX VERSION 1.0.0)

set (CMAKE_CXX_STANDARD 11)

list (APPEND CMAKE_MODULE_PATH "${PROJECT_SOURCE_DIR}/cmake/modules")

find_package (FUSE REQUIRED)

find_library (gRPC_LIBRARIES grpc++ "${GRPC_LIBRARY_DIRS}")
find_library (Protobuf_LIBRARIES protobuf "${PROTOBUF_LIBRARY_DIRS}")

set (GENERATED_GRPC_DIR "${PROJECT_SOURCE_DIR}/build/generated-sources/grpc")
set (GENERATED_PROTOBUF_DIR "${PROJECT_SOURCE_DIR}/build/generated-sources/protobuf")

add_executable (
  paciofs_fuse
  "${PROJECT_SOURCE_DIR}/src/paciofs_fuse.cpp"
  "${GENERATED_GRPC_DIR}/io_posix.grpc.pb.cc"
  "${GENERATED_PROTOBUF_DIR}/io_posix.pb.cc"
)

target_include_directories (
  paciofs_fuse
  PRIVATE
  "${PROJECT_SOURCE_DIR}/include"
  "${GENERATED_GRPC_DIR}"
  "${GENERATED_PROTOBUF_DIR}"
)

# prepend include directories to search path to avoid accidentally using the system headers
target_include_directories (
  paciofs_fuse
  BEFORE PRIVATE
  "${FUSE_INCLUDE_DIRS}"
  "${GRPC_INCLUDE_DIRS}"
  "${PROTOBUF_INCLUDE_DIRS}"
)

target_compile_options (paciofs_fuse PRIVATE -Wall)

target_link_libraries (
  paciofs_fuse
  "${FUSE_LIBRARIES}"
  "${gRPC_LIBRARIES}"
  "${Protobuf_LIBRARIES}"
)
