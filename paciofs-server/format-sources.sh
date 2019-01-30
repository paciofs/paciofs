#!/bin/bash

# -style=file causes clang-format to traverse up the tree of the source file
# until a .clang-format file is found and uses it. .clang-format files are
# located in the subdirectories passed to `find`.
find "$(pwd)/src/main/java"  -name '*.java'  | xargs clang-format -style=file -i
find "$(pwd)/src/test/java"  -name '*.java'  | xargs clang-format -style=file -i
find "$(pwd)/src/main/proto" -name '*.proto' | xargs clang-format -style=file -i
