#############################################
# Messenger Robolectric test target. #
#############################################
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

# Include the testing libraries (JUnit4 + Robolectric libs).
LOCAL_STATIC_JAVA_LIBRARIES := \
    truth-prebuilt \
    mockito-robolectric-prebuilt

LOCAL_JAVA_LIBRARIES := \
    junit \
    platform-robolectric-3.1.1-prebuilt \
    sdk_vcurrent

LOCAL_INSTRUMENTATION_FOR := CarMessengerApp
LOCAL_MODULE := CarMessengerRoboTests

LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_JAVA_LIBRARY)

#############################################################
# Messenger runner target to run the previous target. #
#############################################################
include $(CLEAR_VARS)

LOCAL_MODULE := RunCarMessengerRoboTests

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := \
    CarMessengerRoboTests

LOCAL_TEST_PACKAGE := CarMessengerApp

LOCAL_ROBOTEST_FILES := $(filter-out %/BaseRobolectricTest.java,\
    $(call find-files-in-subdirs,$(LOCAL_PATH)/src,*Test.java,.))

LOCAL_INSTRUMENT_SOURCE_DIRS := $(dir $(LOCAL_PATH))../src

#Require Robolectric 3.4.2 since the min-SDK target is 24
include prebuilts/misc/common/robolectric/3.4.2/run_robotests.mk