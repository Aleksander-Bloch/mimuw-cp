cmake_minimum_required(VERSION 3.22.1)
project(Cyrk)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_FLAGS "-std=c++20 -Wall -Wextra -Werror -pthread")

add_executable(Cyrk demo.cpp system.cpp)
