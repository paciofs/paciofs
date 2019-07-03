#!/bin/bash

find "$(pwd)/include/" -name '*.h' | xargs clang-format -style=file -i
find "$(pwd)/src/"   -name '*.cpp' | xargs clang-format -style=file -i

