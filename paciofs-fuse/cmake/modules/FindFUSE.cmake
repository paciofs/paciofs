if (APPLE)
  set (CMAKE_FIND_FRAMEWORK NEVER)

  add_compile_definitions (_DARWIN_USE_64_BIT_INODE)

  find_path (
    FUSE_INCLUDE_DIRS fuse.h
    /usr/local/include/osxfuse
  )

  set (fuse_name osxfuse)
else ()
  find_path (
    FUSE_INCLUDE_DIRS fuse.h
    /usr/local/include
  )

  set (fuse_name fuse)
endif ()

add_compile_definitions (_FILE_OFFSET_BITS=64)

find_library (
  FUSE_LIBRARIES ${fuse_name}
  /usr/local/lib
)

include (FindPackageHandleStandardArgs)
find_package_handle_standard_args (FUSE DEFAULT_MSG FUSE_INCLUDE_DIRS FUSE_LIBRARIES)

mark_as_advanced (FUSE_INCLUDE_DIRS FUSE_LIBRARIES)