#include <stddef.h>
__attribute__((visibility("hidden"))) void *memcpy(void *dst,const void *src,size_t n){unsigned char *d=dst;const unsigned char *s=src;while(n--)*d++=*s++;return dst;}
__attribute__((visibility("hidden"))) void *memset(void *dst,int c,size_t n){unsigned char *d=dst;while(n--)*d++=(unsigned char)c;return dst;}
