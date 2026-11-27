"""Launch muted packaged clients on a separate Windows desktop without switching input."""
from pathlib import Path
import ctypes as C
from ctypes import wintypes as W
import msvcrt,subprocess,os,time,json,zipfile,argparse,hashlib,uuid,shutil
root=Path(__file__).resolve().parents[1]
proof=root/'build/verification/multiplayer-performance-1112'
k=C.WinDLL('kernel32',use_last_error=True);u=C.WinDLL('user32',use_last_error=True)
class STARTUPINFO(C.Structure):
 _fields_=[('cb',W.DWORD),('lpReserved',W.LPWSTR),('lpDesktop',W.LPWSTR),('lpTitle',W.LPWSTR),*[(n,W.DWORD) for n in ['dwX','dwY','dwXSize','dwYSize','dwXCountChars','dwYCountChars','dwFillAttribute','dwFlags']],('wShowWindow',W.WORD),('cbReserved2',W.WORD),('lpReserved2',C.POINTER(W.BYTE)),('hStdInput',W.HANDLE),('hStdOutput',W.HANDLE),('hStdError',W.HANDLE)]
class PROCESSINFO(C.Structure):_fields_=[('hProcess',W.HANDLE),('hThread',W.HANDLE),('pid',W.DWORD),('tid',W.DWORD)]
u.CreateDesktopW.argtypes=[W.LPCWSTR,W.LPCWSTR,C.c_void_p,W.DWORD,W.DWORD,C.c_void_p];u.CreateDesktopW.restype=W.HANDLE
u.CloseDesktop.argtypes=[W.HANDLE];u.CloseDesktop.restype=W.BOOL
k.CreateProcessW.argtypes=[W.LPCWSTR,W.LPWSTR,C.c_void_p,C.c_void_p,W.BOOL,W.DWORD,C.c_void_p,W.LPCWSTR,C.POINTER(STARTUPINFO),C.POINTER(PROCESSINFO)];k.CreateProcessW.restype=W.BOOL
k.SetHandleInformation.argtypes=[W.HANDLE,W.DWORD,W.DWORD];k.SetProcessAffinityMask.argtypes=[W.HANDLE,C.c_size_t];k.SetProcessAffinityMask.restype=W.BOOL
k.ResumeThread.argtypes=[W.HANDLE];k.ResumeThread.restype=W.DWORD
k.GetExitCodeProcess.argtypes=[W.HANDLE,C.POINTER(W.DWORD)];k.GetExitCodeProcess.restype=W.BOOL
k.WaitForSingleObject.argtypes=[W.HANDLE,W.DWORD];k.WaitForSingleObject.restype=W.DWORD
k.TerminateProcess.argtypes=[W.HANDLE,W.UINT];k.CloseHandle.argtypes=[W.HANDLE]
class Client:
 def __init__(self,command,directory,desktop,cpu):
  self.log=(directory/'client.log').open('wb');self.null=open(os.devnull,'rb');si=STARTUPINFO();si.cb=C.sizeof(si);si.lpDesktop=desktop;si.dwFlags=0x100|0x80
  si.hStdInput=msvcrt.get_osfhandle(self.null.fileno());si.hStdOutput=si.hStdError=msvcrt.get_osfhandle(self.log.fileno())
  for h in [si.hStdInput,si.hStdOutput]:assert k.SetHandleInformation(h,1,1)
  self.info=PROCESSINFO();cmd=C.create_unicode_buffer(subprocess.list2cmdline(command))
  assert k.CreateProcessW(str(command[0]),cmd,None,None,True,0x08000000|0x4000|4,None,str(directory),C.byref(si),C.byref(self.info)),C.WinError(C.get_last_error())
  self.pid=self.info.pid
  try:
   assert k.SetProcessAffinityMask(self.info.hProcess,1<<cpu)
   assert k.ResumeThread(self.info.hThread)!=0xffffffff
  except:
   k.TerminateProcess(self.info.hProcess,1);raise
  finally:k.CloseHandle(self.info.hThread)
 def poll(self):
  code=W.DWORD();assert k.GetExitCodeProcess(self.info.hProcess,C.byref(code));return None if code.value==259 else code.value
 def close(self):
  if self.poll() is None:k.TerminateProcess(self.info.hProcess,1)
  assert k.WaitForSingleObject(self.info.hProcess,5000)==0
  k.CloseHandle(self.info.hProcess);self.log.close();self.null.close()
def client_command(directory,name,options):
 cache=Path(os.environ.get('WEATHER_CLIENT_RUNTIME','D:/MinecraftMineralogy/packaged-runtime-cache/forge-1.11.2-13.20.1.2588-client'));engine=Path(os.environ.get('WEATHER_SERVER_RUNTIME','D:/MinecraftMineralogy/packaged-runtime-cache/forge-1.11.2-13.20.1.2588'))
 assets=Path(os.environ.get('WEATHER_CLIENT_ASSETS',str(cache/'assets')))
 with zipfile.ZipFile(engine/'forge-1.11.2-13.20.1.2588-installer.jar') as z:forge=json.loads(z.read('install_profile.json'))['versionInfo']
 mc=json.loads((cache/'versions/1.11.2/1.11.2.json').read_text());cp=[cache/'versions/1.11.2/1.11.2.jar',engine/'forge-1.11.2-13.20.1.2588-universal.jar'];seen=set()
 def library(path):
  for base in [cache,engine]:
   p=base/'libraries'/path
   if p.is_file():return p
  raise FileNotFoundError(path)
 # Minecraft's Windows runtime takes precedence over legacy launcher dependencies.
 for lib in mc['libraries']+forge['libraries']:
  rules=lib.get('rules',[]);allowed=not rules
  for rule in rules:
   target=rule.get('os',{}).get('name')
   if target is None or target=='windows':allowed=rule['action']=='allow'
  if not allowed or lib['name'].startswith('net.minecraftforge:forge:') or ('natives' in lib and not lib.get('downloads',{}).get('artifact')):continue
  group,artifact,version=lib['name'].split(':')[:3];key=(group,artifact)
  if key in seen:continue
  seen.add(key);cp.append(library(f'{group.replace(".","/")}/{artifact}/{version}/{artifact}-{version}.jar'))
 java=Path.home()/'.gradle/jdks/temurin-8-amd64-windows.2/bin/java.exe'
 return [str(java),'-Xms128m','-Xmx768m','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:CICompilerCount=2','-Dfml.ignoreInvalidMinecraftCertificates=true','-Djava.library.path='+str(cache/'natives'),*['-D'+v for v in options],'-cp',os.pathsep.join(map(str,cp)),'net.minecraft.launchwrapper.Launch','--tweakClass','net.minecraftforge.fml.common.launcher.FMLTweaker','--username',name,'--uuid',uuid.uuid3(uuid.NAMESPACE_DNS,name).hex,'--accessToken','0','--userType','legacy','--version','1.11.2','--gameDir',str(directory),'--assetsDir',str(assets),'--assetIndex',mc['assetIndex']['id'],'--width','800','--height','480']
