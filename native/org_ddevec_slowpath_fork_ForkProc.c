#include <jni.h>

#include <unistd.h>

#include <sys/stat.h>

/*
 * Class:     org_ddevec_slowpath_fork_ForkProc
 * Method:    nativeDoFork
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_ddevec_slowpath_fork_ForkProc_nativeDoFork(JNIEnv *env, jclass cls) {
  return fork();
}

/*
 * Class:     org_ddevec_slowpath_fork_ForkProc
 * Method:    nativeKill
 * Signature: (II)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_ddevec_slowpath_fork_ForkProc_nativeKill(JNIEnv *env, jclass cls,
    jint pid, jint signo) {
  return kill(pid, signo) == -1;
}

/*
 * Class:     org_ddevec_slowpath_fork_ForkProc
 * Method:    createPipe
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_org_ddevec_slowpath_fork_ForkProc_createPipe(JNIEnv *env,
    jclass cls, jstring name, jint mode) {
  const char *pipe_name = (*env)->GetStringUTFChars(env, name, 0);
  jint ret = mkfifo(pipe_name, mode);
  (*env)->ReleaseStringUTFChars(env, name, pipe_name);
  return ret;
}

/*
 * Class:     org_ddevec_slowpath_fork_ForkProc
 * Method:    nativeGetPid
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_ddevec_slowpath_fork_ForkProc_nativeGetPid(
    JNIEnv *pid, jclass cls) {
  return getpid();
}
