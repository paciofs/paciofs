cmake_minimum_required (VERSION 3.1)
project (paciofs_fuse)

set (CMAKE_CXX_STANDARD 11)

set (CMAKE_MODULE_PATH "${CMAKE_MODULE_PATH}" "${CMAKE_SOURCE_DIR}/cmake/modules")

find_package (FUSE REQUIRED)

find_library (gRPC_LIBRARIES grpc ${GRPC_LIBRARY_DIRS})
find_library (Protobuf_LIBRARIES protobuf ${PROTOBUF_LIBRARY_DIRS})

include_directories (
  BEFORE
  "${CMAKE_SOURCE_DIR}/include"
  "${CMAKE_SOURCE_DIR}/build/grpc"
  "${FUSE_INCLUDE_DIRS}"
  "${GRPC_INCLUDE_DIRS}"
  "${PROTOBUF_INCLUDE_DIRS}"
)

add_executable (
  paciofs_fuse
  "${CMAKE_SOURCE_DIR}/build/grpc/io_posix.pb.cc"
  "${CMAKE_SOURCE_DIR}/src/paciofs_fuse.cpp"
)

target_link_libraries (
  paciofs_fuse
  "${FUSE_LIBRARIES}"
  "${gRPC_LIBRARIES}"
  "${Protobuf_LIBRARIES}"
)