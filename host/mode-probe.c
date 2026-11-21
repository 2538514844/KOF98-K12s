#include <jni.h>
#include "libretro.h"

// Host-test-only observer. Never compiled into either Android library.
JNIEXPORT jint JNICALL Java_local_k98_ModeBootTest_byteAt(JNIEnv *env,jclass cls,jint offset) {
    (void)env; (void)cls;
    const uint8_t *ram = retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM);
    size_t size = retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM);
    if (!ram || offset < 0 || (size_t)(offset ^ 1) >= size) return -1;
    // The desktop FBNeo 68K RAM is exposed in host-endian words.
    return ram[offset ^ 1];
}
