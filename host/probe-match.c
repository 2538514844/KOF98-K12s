#include <stdio.h>
#include <stdint.h>
#include <stdarg.h>
#include <string.h>
#include <stdlib.h>
#include "libretro.h"
static int n; static unsigned fmt; static unsigned nonzero; static int stop;
static char keys[768][160], vals[768][128]; static int count;
static void logger(enum retro_log_level l,const char *s,...){va_list a;va_start(a,s);vfprintf(stderr,s,a);va_end(a);}
static bool env(unsigned c,void *p){switch(c){
case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: *(const char**)p="G:/vibecoding/KOF98-K12s/host/system";return true;
case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: *(const char**)p="G:/vibecoding/KOF98-K12s/host/save";return true;
case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:fmt=*(const unsigned*)p;return true;
case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:((struct retro_log_callback*)p)->log=logger;return true;
case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION: *(unsigned*)p=0;return true;
case RETRO_ENVIRONMENT_GET_LANGUAGE: *(unsigned*)p=RETRO_LANGUAGE_ENGLISH;return true;
case RETRO_ENVIRONMENT_GET_CAN_DUPE:*(bool*)p=true;return true;
case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE: *(unsigned*)p=3;return true;
case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:*(bool*)p=false;return true;
case RETRO_ENVIRONMENT_SET_VARIABLES:{const struct retro_variable *v=p;count=0;for(;v&&v->key&&count<768;v++){const char *k=v->key,*st=v->value?strchr(v->value,';'):NULL;if(!st)continue;while(*++st==' ');size_t len=strcspn(st,"|");if(len>=sizeof(vals[0]))len=sizeof(vals[0])-1;snprintf(keys[count],sizeof(keys[0]),"%s",k);memcpy(vals[count],st,len);vals[count][len]=0;if(strstr(k,"neogeo-mode")){fprintf(stderr,"Neo mode choices: %s\n",v->value);if(strstr(st,"AES"))strcpy(vals[count],"AES_EUR");}count++;}return true;}
case RETRO_ENVIRONMENT_GET_VARIABLE:{struct retro_variable *v=p;v->value=NULL;for(int i=0;i<count;i++){if(!strcmp(v->key,keys[i])){v->value=vals[i];return true;}}return false;}
case RETRO_ENVIRONMENT_SET_MESSAGE:fprintf(stderr,"MESSAGE: %s\n",((struct retro_message*)p)->msg);return true;
case RETRO_ENVIRONMENT_SET_MESSAGE_EXT:fprintf(stderr,"MESSAGE: %s\n",((struct retro_message_ext*)p)->msg);return true;
case RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION: *(unsigned*)p=1;return true;
case RETRO_ENVIRONMENT_SHUTDOWN:stop=1;return true;
default:return false;}}
static void video(const void *data,unsigned w,unsigned h,size_t pitch){if(!data||data==(void*)-1||(n%240!=0))return;char path[260];snprintf(path,sizeof(path),"G:/vibecoding/KOF98-K12s/evidence/match-%04d.ppm",n);FILE *f=fopen(path,"wb");if(!f)return;fprintf(f,"P6\n%u %u\n255\n",w,h);for(unsigned y=0;y<h;y++){const unsigned char *row=(const unsigned char *)data+y*pitch;for(unsigned x=0;x<w;x++){uint32_t r,g,b;if(fmt==RETRO_PIXEL_FORMAT_XRGB8888){uint32_t u=((const uint32_t*)row)[x];r=(u>>16)&255;g=(u>>8)&255;b=u&255;}else{uint16_t u=((const uint16_t*)row)[x];if(fmt==RETRO_PIXEL_FORMAT_RGB565){r=((u>>11)&31)*255/31;g=((u>>5)&63)*255/63;}else{r=((u>>10)&31)*255/31;g=((u>>5)&31)*255/31;}b=(u&31)*255/31;}fputc(r,f);fputc(g,f);fputc(b,f);}}fclose(f);}
static void sample(int16_t l,int16_t r){if(l||r)++nonzero;}
static size_t batch(const int16_t *p,size_t k){if(p)for(size_t i=0;i<k;i++)if(p[2*i]||p[2*i+1])++nonzero;return k;}
static void poller(void){}
static int16_t inp(unsigned port,unsigned dev,unsigned idx,unsigned id){if(port||dev!=RETRO_DEVICE_JOYPAD)return 0;if(id==RETRO_DEVICE_ID_JOYPAD_START && (n>=610&&n<1400&&(n%75)<6))return 1;if(id==RETRO_DEVICE_ID_JOYPAD_B && n>720&&(n%39)<5)return 1;return 0;}
int main(int argc,char **argv){if(argc!=2)return 2;setvbuf(stdout,NULL,_IONBF,0);setvbuf(stderr,NULL,_IONBF,0);retro_set_environment(env);retro_set_video_refresh(video);retro_set_audio_sample(sample);retro_set_audio_sample_batch(batch);retro_set_input_poll(poller);retro_set_input_state(inp);retro_init();struct retro_system_info si={0};retro_get_system_info(&si);printf("%s %s\n",si.library_name,si.library_version);struct retro_game_info gi={argv[1],NULL,0,NULL};if(!retro_load_game(&gi)){fprintf(stderr,"Game load refused\n");retro_deinit();return 3;}struct retro_system_av_info av={0};retro_get_system_av_info(&av);printf("geometry %u x %u, fps %.3f\n",av.geometry.base_width,av.geometry.base_height,av.timing.fps);for(n=0;n<6000&&!stop;n++)retro_run();printf("frames %d; non-silent audio frames: %u\n",n,nonzero);retro_unload_game();retro_deinit();return 0;}
