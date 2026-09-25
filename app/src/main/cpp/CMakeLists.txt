cmake_minimum_required(VERSION 3.22.1)
project(occt_step_bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(OCCT_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../occt/${ANDROID_ABI}")
set(OCCT_JNILIB_DIR "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}")

set(OCCT_INCLUDE_DIR "${OCCT_ROOT}/include/opencascade")

if(NOT EXISTS "${OCCT_INCLUDE_DIR}/STEPControl_Reader.hxx")
    message(FATAL_ERROR "OCCT STEP headers not found at ${OCCT_INCLUDE_DIR}. Build/provision OCCT before Gradle.")
endif()

file(GLOB OCCT_SHARED_LIBS
    "${OCCT_ROOT}/lib/*.so"
    "${OCCT_ROOT}/lib64/*.so"
)

if(NOT OCCT_SHARED_LIBS)
    message(FATAL_ERROR "OCCT shared libraries not found under ${OCCT_ROOT}.")
endif()

add_library(occt_step_bridge SHARED occt_step_bridge.cpp)

target_include_directories(occt_step_bridge PRIVATE "${OCCT_INCLUDE_DIR}")

target_compile_options(occt_step_bridge PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fexceptions
    -frtti
)

target_link_libraries(occt_step_bridge PRIVATE
    ${OCCT_SHARED_LIBS}
    android
    log
)
