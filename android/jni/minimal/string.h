#include <stddef.h>
__attribute__((visibility("hidden"))) void *memcpy(void *, const void *,size_t);
__attribute__((visibility("hidden"))) void *memset(void *,int,size_t);
static inline size_t strlen(const char *s){const char *p=s;while(*p)p++;return (size_t)(p-s);}
static inline char *strchr(const char *s,int c){while(*s!=(char)c){if(!*s++)return NULL;}return (char*)s;}
static inline size_t strcspn(const char *s,const char *reject){const char *p=s;while(*p&&!strchr(reject,*p))++p;return (size_t)(p-s);}
static inline int strcmp(const char *a,const char *b){while(*a&&*a==*b){a++;b++;}return (int)(unsigned char)*a-(unsigned char)*b;}
static inline char *strcpy(char *d,const char *s){char *p=d;while((*p++=*s++)){}return d;}
