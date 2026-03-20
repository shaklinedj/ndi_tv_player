cmake_minimum_required(VERSION 3.22.1)
project("ndijni")

# Include NDI header files
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/Include)

# Set path to the prebuilt NDI library
set(NDI_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(
        ndi-lib
        SHARED
        IMPORTED)

set_target_properties(
        ndi-lib
        PROPERTIES IMPORTED_LOCATION
        ${NDI_LIB_DIR}/libndi.so)

add_library(
        ndijni
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)
        
find_library(
        android-lib
        android)

target_link_libraries(
        ndijni
        ndi-lib
        ${log-lib}
        ${android-lib})
