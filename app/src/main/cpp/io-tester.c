#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include "io-tester.h"
#include <jni.h>
#include <android/log.h>
#include <errno.h>

#define FILESIZE 104857600 //100MiB

#define BUF_SIZE_4K 4096
#define BUF_SIZE_128K   524288

static const char* kTAG = "io_testerjni";
#define LOGI(...) \
  ((void)__android_log_print(ANDROID_LOG_INFO, kTAG, __VA_ARGS__))
#define LOGW(...) \
  ((void)__android_log_print(ANDROID_LOG_WARN, kTAG, __VA_ARGS__))
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, kTAG, __VA_ARGS__))

char buf4k[BUF_SIZE_4K];
char buf128k[BUF_SIZE_128K];

JNIEXPORT jint JNICALL
Java_com_oscar_WearOutAttack_Dynamo_NativeSyncOpen( JNIEnv* env, jobject thiz, jstring jfilename ) {
    const char *filename = (*env)->GetStringUTFChars(env, jfilename, NULL);
    int fd = open(filename, O_CREAT | O_RDWR | O_SYNC, S_IRWXU | S_IRWXG);
    LOGI("file %s opened with fd %d", filename, fd);

    if (fd < 0)
        LOGE("%s open %s failed with errno %d", __func__, filename, errno);
    return fd;
}

JNIEXPORT jint JNICALL
Java_com_oscar_WearOutAttack_Dynamo_NativeSyncFSync( JNIEnv* env, jobject thiz, jint fd ) {
    return fsync(fd);
}

JNIEXPORT jint JNICALL
Java_com_oscar_WearOutAttack_Dynamo_NativeSyncWrite4K( JNIEnv* env, jobject thiz, jint fd ) {
    off_t pos = lseek(fd, 0, SEEK_CUR);

    if (pos >= FILESIZE)
        lseek(fd, 0, SEEK_SET);

    pos = write(fd, buf4k, BUF_SIZE_4K);

    if (pos < 0)
        LOGE("%s fd %d failed with %d errno = %d", fd, __func__, pos, errno);

    return pos;
}

JNIEXPORT jint JNICALL
Java_com_oscar_WearOutAttack_Dynamo_NativeSyncWrite128K( JNIEnv* env, jobject thiz, jint fd ) {
    off_t pos = lseek(fd, 0, SEEK_CUR);

    if (pos >= FILESIZE)
        lseek(fd, 0, SEEK_SET);

    pos = write(fd, buf128k, BUF_SIZE_128K);

    if (pos < 0)
        LOGE("%s fd %d failed with %d errno = %d", fd, __func__, pos, errno);

    return pos;
}

JNIEXPORT jint JNICALL
Java_com_oscar_WearOutAttack_Dynamo_NativeSyncInit( JNIEnv* env, jobject thiz ) {
    int i;
    LOGI("%s called", __func__);

    for (i = 0; i < BUF_SIZE_4K; i++) {
        buf4k[i] = i;
    }

    for (i = 0; i < BUF_SIZE_128K; i++) {
        buf128k[i] = i;
    }
}

JNIEXPORT jint JNICALL
Java_com_oscar_WearOutAttack_Dynamo_NativeSyncCleanup( JNIEnv* env, jobject thiz ) {
    return 0;
}