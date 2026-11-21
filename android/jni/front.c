#include <jni.h>
#include <stdint.h>
#include <stdarg.h>
#include <string.h>
#include "libretro.h"
#ifdef _WIN32
#include <stdio.h>
#define LOCAL_SEP "\\"
#else
#define LOCAL_SEP "/"
#endif

#define JS(n) Java_local_k98_Bridge_##n
#define PIXELS (640*512)
#define PAIRS 4096
static uint32_t image[PIXELS];
static int16_t samples[PAIRS*2];
static unsigned w,h,fmt,used,keys; static int loaded,init,halted,picture;
static char systemdir[1024],savedir[1024],game[1024];
struct neo_profile { const char *id; const char *bios_dip; };
static const struct neo_profile modes[]={
 {"AES_EUR","AES World"},
 {"AES_JAP","AES Japan"},
 {"MVS_EUR","MVS Asia/Europe ver. 6 (1 slot)"},
 {"MVS_USA","MVS USA ver. 5 (2 slot)"},
 {"MVS_JAP","MVS Japan ver. 6 (? slot)"}
};
static const struct neo_profile *active_mode=&modes[0];
static int bios_fallback;
static char option_keys[768][128],option_values[768][128]; static unsigned nopts;
static struct retro_system_av_info av;
static int concat(char *out,size_t cap,const char *base,const char *suffix){size_t a=strlen(base),b=strlen(suffix);if(a+b>=cap)return 0;memcpy(out,base,a);memcpy(out+a,suffix,b+1);return 1;}
static void logcb(enum retro_log_level level,const char *form,...){(void)level;
 // FBNeo may successfully boot a different machine when the requested BIOS is absent.
 if(form && !strcmp(form,"No bios found for requested mode, falling back to DIPSWITCH => NeoSystem: 0x%02x.\n"))bios_fallback=1;
#ifdef _WIN32
 va_list args;va_start(args,form);vfprintf(stderr,form,args);va_end(args);
#else
 (void)form;
#endif
}
static bool envcb(unsigned cmd,void *p){switch(cmd){
 case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:*(const char**)p=systemdir;return true;
 case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:*(const char**)p=savedir;return true;
 case RETRO_ENVIRONMENT_GET_CAN_DUPE:*(bool*)p=true;return true;
 case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:fmt=*(const unsigned*)p;return fmt<=RETRO_PIXEL_FORMAT_RGB565;
 case RETRO_ENVIRONMENT_GET_LANGUAGE:*(unsigned*)p=RETRO_LANGUAGE_ENGLISH;return true;
 case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:((struct retro_log_callback*)p)->log=logcb;return true;
 case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:*(unsigned*)p=0;return true;
 case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:*(unsigned*)p=3;return true;
 case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:*(bool*)p=false;return true;
 case RETRO_ENVIRONMENT_SET_GEOMETRY:av.geometry=*(struct retro_game_geometry*)p;return true;
 case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:av=*(struct retro_system_av_info*)p;return true;
 case RETRO_ENVIRONMENT_SHUTDOWN:halted=1;return true;
 case RETRO_ENVIRONMENT_SET_VARIABLES:{const struct retro_variable *v=p;nopts=0;for(;v&&v->key&&nopts<768;v++){const char *s=v->value?strchr(v->value,';'):0;if(!s)continue;for(++s;*s==' ';++s){} size_t a=strlen(v->key),b=strcspn(s,"|");if(a>=128||b>=128)continue;memcpy(option_keys[nopts],v->key,a+1);memcpy(option_values[nopts],s,b);option_values[nopts][b]=0;
  if(!strcmp(v->key,"fbneo-neogeo-mode"))strcpy(option_values[nopts],active_mode->id);
  // After first-boot MVS backup RAM initialization, the board reboots from the BIOS DIP.
  if(!strcmp(v->key,"fbneo-dipswitch-kof98-BIOS"))strcpy(option_values[nopts],active_mode->bios_dip);
  if(!strcmp(v->key,"fbneo-allow-patched-romsets"))strcpy(option_values[nopts],"enabled");
  ++nopts;}return true;}
 case RETRO_ENVIRONMENT_GET_VARIABLE:{struct retro_variable *v=p;v->value=0;for(unsigned i=0;i<nopts;i++)if(!strcmp(v->key,option_keys[i])){v->value=option_values[i];return true;}return false;}
 default:return false;}}
static void vid(const void *data,unsigned width,unsigned height,size_t pitch){if(!data||data==(void*)-1||!width||!height||width>640||height>512)return;w=width;h=height;picture=1;for(unsigned y=0;y<h;y++){const uint8_t *row=(const uint8_t*)data+pitch*y;for(unsigned x=0;x<w;x++){uint32_t c;
 if(fmt==RETRO_PIXEL_FORMAT_XRGB8888)c=((const uint32_t*)row)[x]&0xffffff;else{uint16_t v=((const uint16_t*)row)[x];unsigned r,g,b;if(fmt==RETRO_PIXEL_FORMAT_RGB565){r=(v>>11)&31;g=(v>>5)&63;g=(g<<2)|(g>>4);}else{r=(v>>10)&31;g=(v>>5)&31;g=(g<<3)|(g>>2);}b=v&31;c=(((r<<3)|(r>>2))<<16)|(g<<8)|((b<<3)|(b>>2));}image[y*w+x]=0xff000000U|c;}}}
static size_t bat(const int16_t *s,size_t frames){size_t n=frames;if(n>PAIRS-used)n=PAIRS-used;if(s&&n){memcpy(samples+2*used,s,n*4);used+=(unsigned)n;}return frames;}
static void snd(int16_t l,int16_t r){if(used<PAIRS){samples[2*used]=l;samples[2*used+1]=r;++used;}}
static void poll(void){}
static int16_t inp(unsigned port,unsigned dev,unsigned index,unsigned id){return !port&&((dev&RETRO_DEVICE_MASK)==RETRO_DEVICE_JOYPAD)&&!index&&id<16?((keys>>id)&1):0;}
static void close_core(void){if(loaded){retro_unload_game();loaded=0;}if(init){retro_deinit();init=0;}}
static int copy_path(JNIEnv *env,jstring value,char *dest,size_t capacity,const char *suffix){
 if(!value)return 0;
 const char *path=(*env)->GetStringUTFChars(env,value,NULL);
 if(!path)return 0;
 int ok=concat(dest,capacity,path,suffix);
 (*env)->ReleaseStringUTFChars(env,value,path);
 return ok;
}
JNIEXPORT jint JNICALL JS(openMode)(JNIEnv *e,jclass cls,jstring root,jstring system,jstring save,jstring mode){
 (void)cls;
 close_core();
 if(!copy_path(e,root,game,sizeof(game),LOCAL_SEP "roms" LOCAL_SEP "kof98.zip") ||
    !copy_path(e,system,systemdir,sizeof(systemdir),"") ||
    !copy_path(e,save,savedir,sizeof(savedir),""))return 1;
 char requested[24];
 if(!copy_path(e,mode,requested,sizeof(requested),""))return 4;
 active_mode=NULL;
 for(size_t i=0;i<sizeof(modes)/sizeof(modes[0]);i++)
     if(!strcmp(requested,modes[i].id)){active_mode=&modes[i];break;}
 if(!active_mode)return 4;
 bios_fallback=halted=picture=0;nopts=fmt=used=keys=w=h=0;
 retro_set_environment(envcb);retro_set_video_refresh(vid);retro_set_audio_sample(snd);retro_set_audio_sample_batch(bat);retro_set_input_poll(poll);retro_set_input_state(inp);
 retro_init();init=1;
 struct retro_game_info gi={game,NULL,0,NULL};
 if(!retro_load_game(&gi)){close_core();return 2;}
 loaded=1;
 if(bios_fallback){close_core();return 5;}
 retro_get_system_av_info(&av);
 if(av.geometry.base_width>400||av.geometry.base_height>300||av.timing.fps<45.0||av.timing.fps>65.0||av.timing.sample_rate<8000.0||av.timing.sample_rate>96000.0){close_core();return 3;}
 return 0;
}
JNIEXPORT jint JNICALL JS(rate)(JNIEnv *e,jclass c){(void)e;(void)c;return (jint)(av.timing.sample_rate+0.5);}
JNIEXPORT jdouble JNICALL JS(fps)(JNIEnv *e,jclass c){(void)e;(void)c;return av.timing.fps;}
JNIEXPORT jint JNICALL JS(frame)(JNIEnv *e,jclass c,jint mask,jintArray px,jshortArray pcm,jintArray geom){(void)c;if(!loaded||halted)return -1;keys=(unsigned)mask;used=0;picture=0;retro_run();jint wh[2]={(jint)w,(jint)h};(*e)->SetIntArrayRegion(e,geom,0,2,wh);if(picture)(*e)->SetIntArrayRegion(e,px,0,(jsize)(w*h),(const jint*)image);if(used)(*e)->SetShortArrayRegion(e,pcm,0,(jsize)(2*used),(const jshort*)samples);return (jint)used;}
JNIEXPORT jint JNICALL JS(stateSize)(JNIEnv *e,jclass c){(void)e;(void)c;if(!loaded)return 0;size_t n=retro_serialize_size();return n&&n<=32*1024*1024?(jint)n:0;}
JNIEXPORT jboolean JNICALL JS(state)(JNIEnv *e,jclass c,jbyteArray bytes,jboolean writing){(void)c;if(!loaded||!bytes)return 0;jsize n=(*e)->GetArrayLength(e,bytes);if(n<=0||(size_t)n!=retro_serialize_size())return 0;jbyte *b=(*e)->GetByteArrayElements(e,bytes,0);if(!b)return 0;int ok=writing?retro_serialize(b,n):retro_unserialize(b,n);(*e)->ReleaseByteArrayElements(e,bytes,b,(writing&&ok)?0:JNI_ABORT);return ok?JNI_TRUE:JNI_FALSE;}
JNIEXPORT void JNICALL JS(close)(JNIEnv *e,jclass c){(void)e;(void)c;close_core();}
