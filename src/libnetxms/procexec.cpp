/*
** NetXMS - Network Management System
** Copyright (C) 2003-2022 Raden Solutions
**
** This program is free software; you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation; either version 2 of the License, or
** (at your option) any later version.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with this program; if not, write to the Free Software
** Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
**
** File: procexec.cpp
**
**/

#include "libnetxms.h"
#include <nxproc.h>
#include <signal.h>

#if HAVE_SYS_WAIT_H
#include <sys/wait.h>
#endif

#define DEBUG_TAG _T("procexec")

#ifdef _WIN32

/**
 * Next free pipe ID
 */
static VolatileCounter s_pipeId = 0;

/**
 * Create pipe
 */
static bool CreatePipeEx(LPHANDLE lpReadPipe, LPHANDLE lpWritePipe, bool asyncRead)
{
   TCHAR name[MAX_PATH];
   _sntprintf(name, MAX_PATH, _T("\\\\.\\Pipe\\nxexec.%08x.%08x"), GetCurrentProcessId(), InterlockedIncrement(&s_pipeId));

	SECURITY_ATTRIBUTES sa;
	sa.nLength = sizeof(SECURITY_ATTRIBUTES);
	sa.lpSecurityDescriptor = NULL;
	sa.bInheritHandle = TRUE;

   HANDLE readHandle = CreateNamedPipe(
      name,
      PIPE_ACCESS_INBOUND | (asyncRead ? FILE_FLAG_OVERLAPPED : 0),
      PIPE_TYPE_BYTE | PIPE_WAIT,
      1,           // Number of pipes
      8192,        // Out buffer size
      8192,        // In buffer size
      60000,       // Timeout in ms
      &sa
   );
   if (readHandle == INVALID_HANDLE_VALUE)
      return false;

   HANDLE writeHandle = CreateFile(
      name,
      GENERIC_WRITE,
      0,                         // No sharing
      &sa,
      OPEN_EXISTING,
      FILE_ATTRIBUTE_NORMAL,
      NULL                       // Template file
   );
   if (writeHandle == INVALID_HANDLE_VALUE)
   {
      DWORD error = GetLastError();
      CloseHandle(readHandle);
      SetLastError(error);
      return false;
   }

   *lpReadPipe = readHandle;
   *lpWritePipe = writeHandle;
   return true;
}

#endif /* _WIN32 */

/**
 * Next free executor ID
 */
static VolatileCounter s_executorId = 0;

/**
 * Create new process executor object for given command line
 */
ProcessExecutor::ProcessExecutor(const TCHAR *cmd, bool shellExec, bool selfDestruct) : m_completed(true)
{
   m_id = InterlockedIncrement(&s_executorId);
#ifdef _WIN32
   m_phandle = INVALID_HANDLE_VALUE;
   m_pipe = INVALID_HANDLE_VALUE;
#else
   m_pid = 0;
   m_pipe[0] = -1;
   m_pipe[1] = -1;
#endif
   m_cmd = MemCopyString(cmd);
   m_shellExec = shellExec;
   m_sendOutput = false;
   m_replaceNullCharacters = false;
   m_selfDestruct = selfDestruct;
   m_outputThread = INVALID_THREAD_HANDLE;
   m_started = false;
   m_running = false;
   m_exitCode = -1;
}

/**
 * Destructor
 */
ProcessExecutor::~ProcessExecutor()
{
   stop();
   ThreadJoin(m_outputThread);
   MemFree(m_cmd);
#ifdef _WIN32
   if (m_phandle != INVALID_HANDLE_VALUE)
      CloseHandle(m_phandle);
#endif
}

#ifdef _WIN32

/**
 * Set explicit list of inherited handles
 */
static bool SetInheritedHandle(STARTUPINFOEX *si, HANDLE *handle)
{
   SIZE_T size;
   if (!InitializeProcThreadAttributeList(nullptr, 1, 0, &size))
   {
      if (GetLastError() != ERROR_INSUFFICIENT_BUFFER)
      {
         TCHAR buffer[1024];
         nxlog_debug_tag(DEBUG_TAG, 4, _T("ProcessExecutor::execute(): InitializeProcThreadAttributeList failed (%s)"), GetSystemErrorText(GetLastError(), buffer, 1024));
         return false;
      }
   }
   si->lpAttributeList = static_cast<LPPROC_THREAD_ATTRIBUTE_LIST>(MemAlloc(size));

   if (!InitializeProcThreadAttributeList(si->lpAttributeList, 1, 0, &size))
   {
      TCHAR buffer[1024];
      nxlog_debug_tag(DEBUG_TAG, 4, _T("ProcessExecutor::execute(): InitializeProcThreadAttributeList failed (%s)"), GetSystemErrorText(GetLastError(), buffer, 1024));
      MemFree(si->lpAttributeList);
      return false;
   }

   if (!UpdateProcThreadAttribute(si->lpAttributeList, 0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST, handle, sizeof(HANDLE), nullptr, nullptr))
   {
      TCHAR buffer[1024];
      nxlog_debug_tag(DEBUG_TAG, 4, _T("ProcessExecutor::execute(): UpdateProcThreadAttribute failed (%s)"), GetSystemErrorText(GetLastError(), buffer, 1024));
      MemFree(si->lpAttributeList);
      return false;
   }

   return true;
}

/**
 * Execute command with output capture
 */
bool ProcessExecutor::executeWithOutput()
{
   SECURITY_ATTRIBUTES sa;
   sa.nLength = sizeof(SECURITY_ATTRIBUTES);
   sa.lpSecurityDescriptor = nullptr;
   sa.bInheritHandle = TRUE;

   HANDLE stdoutRead, stdoutWrite;
   if (!CreatePipeEx(&stdoutRead, &stdoutWrite, true))
   {
      TCHAR buffer[1024];
      nxlog_debug_tag(DEBUG_TAG, 4, _T("ProcessExecutor::execute(): cannot create pipe (%s)"), GetSystemErrorText(GetLastError(), buffer, 1024));
      return false;
   }

   // Ensure the read handle to the pipe for STDOUT is not inherited.
   SetHandleInformation(stdoutRead, HANDLE_FLAG_INHERIT, 0);

   STARTUPINFOEX si;
   PROCESS_INFORMATION pi;

   memset(&si, 0, sizeof(STARTUPINFOEX));
   si.StartupInfo.cb = sizeof(STARTUPINFOEX);
   si.StartupInfo.dwFlags = STARTF_USESTDHANDLES;
   si.StartupInfo.hStdOutput = stdoutWrite;
   si.StartupInfo.hStdError = stdoutWrite;
   SetInheritedHandle(&si, &stdoutWrite);

   bool success = false;

   StringBuffer cmdLine = m_shellExec ? _T("CMD.EXE /C ") : _T("");
   cmdLine.append(m_cmd);
   if (CreateProcess(nullptr, cmdLine.getBuffer(), nullptr, nullptr, TRUE, EXTENDED_STARTUPINFO_PRESENT, nullptr, nullptr, &si.StartupInfo, &pi))
   {
      nxlog_debug_tag(DEBUG_TAG, 5, _T("ProcessExecutor::execute(): process \"%s\" started"), cmdLine.cstr());

      m_phandle = pi.hProcess;
      CloseHandle(pi.hThread);
      CloseHandle(stdoutWrite);
      m_pipe = stdoutRead;
      m_outputThread = ThreadCreateEx(readOutput, this);
      success = true;
   }
   else
   {
      TCHAR buffer[1024];
      nxlog_debug_tag(DEBUG_TAG, 4, _T("ProcessExecutor::execute(): cannot create process \"%s\" (Error 0x%08x: %s)"),
         cmdLine.getBuffer(), GetLastError(), GetSystemErrorText(GetLastError(), buffer, 1024));

      CloseHandle(stdoutRead);
      CloseHandle(stdoutWrite);
   }

   DeleteProcThreadAttributeList(si.lpAttributeList);
   MemFree(si.lpAttributeList);
   return success;
}

/**
 * Execute command without output capture
 */
bool ProcessExecutor::executeWithoutOutput()
{
   StringBuffer cmdLine = m_shellExec ? _T("CMD.EXE /C ") : _T("");
   cmdLine.append(m_cmd);

   STARTUPINFO si;
   memset(&si, 0, sizeof(STARTUPINFO));
   si.cb = sizeof(STARTUPINFO);

   PROCESS_INFORMATION pi;
   if (!CreateProcess(nullptr, cmdLine.getBuffer(), nullptr, nullptr, FALSE, 0, nullptr, nullptr, &si, &pi))
   {
      TCHAR buffer[1024];
      nxlog_debug_tag(DEBUG_TAG, 4, _T("ProcessExecutor::execute(): cannot create process \"%s\" (Error 0x%08x: %s)"),
         cmdLine.getBuffer(), GetLastError(), GetSystemErrorText(GetLastError(), buffer, 1024));
      return false;
   }

   nxlog_debug_tag(DEBUG_TAG, 5, _T("ProcessExecutor::execute(): process \"%s\" started"), cmdLine.cstr());
   m_phandle = pi.hProcess;
   CloseHandle(pi.hThread);
   return true;
}

#endif   /* _WIN32 */

/**
 * Execute command
 */
bool ProcessExecutor::execute()
{
   if (isRunning())
      return false;  // Process already running

   if (m_outputThread != INVALID_THREAD_HANDLE)
   {
      ThreadJoin(m_outputThread);
      m_outputThread = INVALID_THREAD_HANDLE;
   }
   m_completed.reset();

   bool success = false;

#ifdef _WIN32  /* Windows implementation */

   if (m_phandle != INVALID_HANDLE_VALUE)
   {
      CloseHandle(m_phandle);
      m_phandle = INVALID_HANDLE_VALUE;
   }

   success = m_sendOutput ? executeWithOutput() : executeWithoutOutput();

#else /* UNIX implementation */

   int fd;

   if (pipe(m_pipe) == -1)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("ProcessExecutor::execute(): pipe() call failed (%s)"), _tcserror(errno));
      return false;
   }

   m_pid = fork();
   switch(m_pid)
   {
      case -1: // error
         nxlog_debug_tag(DEBUG_TAG, 4, _T("ProcessExecutor::execute(): fork() call failed (%s)"), _tcserror(errno));
         close(m_pipe[0]);
         close(m_pipe[1]);
         break;
      case 0: // child
         setpgid(0, 0); // new process group
         close(m_pipe[0]);
         dup2(m_pipe[1], STDOUT_FILENO);
         dup2(m_pipe[1], STDERR_FILENO);
         close(m_pipe[1]);
	 fd = open("/dev/null", O_RDONLY);
	 if (fd != -1)
	 {
            dup2(fd, STDIN_FILENO);
	    close(fd);
	 }
	 else
	 {
            close(STDIN_FILENO);
	 }
         if (m_shellExec)
         {
#ifdef UNICODE
            execl("/bin/sh", "/bin/sh", "-c", MBStringFromWideStringSysLocale(m_cmd), nullptr);
#else
            execl("/bin/sh", "/bin/sh", "-c", m_cmd, nullptr);
#endif
         }
         else
         {
#ifdef UNICODE
            char *tmp = MBStringFromWideStringSysLocale(m_cmd);
#else
            char *tmp = m_cmd;
#endif

            char *argv[256];
            argv[0] = tmp;

            int index = 1;
            bool squotes = false, dquotes = false;
            for(char *p = tmp; *p != 0;)
            {
               if ((*p == ' ') && !squotes && !dquotes)
               {
                  *p = 0;
                  p++;
                  while(*p == ' ')
                     p++;
                  argv[index++] = p;
               }
               else if ((*p == '\'') && !dquotes)
               {
                  squotes = !squotes;
                  memmove(p, p + 1, strlen(p));
               }
               else if ((*p == '"') && !squotes)
               {
                  dquotes = !dquotes;
                  memmove(p, p + 1, strlen(p));
               }
               else if ((*p == '\\') && !squotes)
               {
                  // Follow shell convention for backslash:
                  // A backslash preserves the literal meaning of the following character, with the exception of ⟨newline⟩.
                  // Enclosing characters within double quotes preserves the literal meaning of all characters except dollarsign ($), backquote (`), and backslash (\).
                  // The backslash inside double quotes is historically weird, and serves to quote only the following characters:
                  //          $ ` " \ <newline>.
                  // Otherwise it remains literal.
                  if (!dquotes || ((*(p + 1) == '"') || (*(p + 1) == '\\') || (*(p + 1) == '`') || (*(p + 1) == '$')))
                  {
                     memmove(p, p + 1, strlen(p));
                     p++;
                  }
               }
               else
               {
                  p++;
               }
            }
            argv[index] = nullptr;
            execv(argv[0], argv);
         }

         // exec failed
         char errorMessage[1024];
         snprintf(errorMessage, 1024, "Cannot start process (%s)\n", strerror(errno));
         write(STDERR_FILENO, errorMessage, strlen(errorMessage));
         _exit(127);
         break;

      default: // parent
         close(m_pipe[1]);
         nxlog_debug_tag(DEBUG_TAG, 5, _T("ProcessExecutor::execute(): process \"%s\" started"), m_cmd);
         if (m_sendOutput)
         {
            m_outputThread = ThreadCreateEx(readOutput, this);
         }
         else
         {
            close(m_pipe[0]);
            m_outputThread = ThreadCreateEx(waitForProcess, this);
         }
         success = true;
         break;
   }

#endif

   m_started = true;
   m_running = success;
   return success;
}

#ifndef _WIN32

/**
 * Process waiting thread
 */
void ProcessExecutor::waitForProcess(ProcessExecutor *executor)
{
   waitpid(executor->m_pid, nullptr, 0);
   executor->m_running = false;
   executor->m_completed.set();
   if (executor->m_selfDestruct)
      delete executor;
}

#endif

/**
 * Output reading thread
 */
void ProcessExecutor::readOutput(ProcessExecutor *executor)
{
   char buffer[4096];

#ifdef _WIN32  /* Windows implementation */

   OVERLAPPED ov;
	memset(&ov, 0, sizeof(OVERLAPPED));
   ov.hEvent = CreateEvent(nullptr, TRUE, FALSE, nullptr);

   HANDLE pipe = executor->getOutputPipe();
   while(true)
   {
      if (!ReadFile(pipe, buffer, sizeof(buffer) - 1, nullptr, &ov))
      {
         if (GetLastError() != ERROR_IO_PENDING)
         {
            TCHAR emsg[1024];
            nxlog_debug_tag(DEBUG_TAG, 6, _T("ProcessExecutor::readOutput(): stopped on ReadFile (%s)"), GetSystemErrorText(GetLastError(), emsg, 1024));
            break;
         }
      }

      HANDLE handles[2];
      handles[0] = ov.hEvent;
      handles[1] = executor->m_phandle;
      DWORD rc;

do_wait:
      rc = WaitForMultipleObjects(2, handles, FALSE, 5000);
      if (rc == WAIT_TIMEOUT)
      {
         // Send empty output on timeout
         executor->onOutput("", 0);
         goto do_wait;
      }
      if (rc == WAIT_OBJECT_0 + 1)
      {
         nxlog_debug_tag(DEBUG_TAG, 6, _T("ProcessExecutor::readOutput(): process termination detected"));
         break;   // Process terminated
      }
      if (rc == WAIT_OBJECT_0)
      {
         DWORD bytes;
         if (GetOverlappedResult(pipe, &ov, &bytes, TRUE))
         {
            if (executor->m_replaceNullCharacters)
            {
               for (DWORD i = 0; i < bytes; i++)
                  if (buffer[i] == 0)
                     buffer[i] = ' ';
            }
            buffer[bytes] = 0;
            executor->onOutput(buffer, bytes);
         }
         else
         {
            TCHAR emsg[1024];
            nxlog_debug_tag(DEBUG_TAG, 6, _T("ProcessExecutor::readOutput(): stopped on GetOverlappedResult (%s)"), GetSystemErrorText(GetLastError(), emsg, 1024));
            break;
         }
      }
   }

   DWORD exitCode;
   if (GetExitCodeProcess(executor->m_phandle, &exitCode))
      executor->m_exitCode = exitCode;
   else
      executor->m_exitCode = -1;

   CloseHandle(ov.hEvent);
   CloseHandle(pipe);

#else /* UNIX implementation */

   int pipe = executor->getOutputPipe();
   fcntl(pipe, F_SETFD, fcntl(pipe, F_GETFD) | O_NONBLOCK);

   SocketPoller sp;
   while(true)
   {
      sp.reset();
      sp.add(pipe);
      int rc = sp.poll(10000);
      if (rc > 0)
      {
         rc = read(pipe, buffer, sizeof(buffer) - 1);
         if (rc > 0)
         {
            if (executor->m_replaceNullCharacters)
            {
               for (int i = 0; i < rc; i++)
                  if (buffer[i] == 0)
                     buffer[i] = ' ';
            }
            buffer[rc] = 0;
            executor->onOutput(buffer, rc);
         }
         else
         {
            if ((rc == -1) && ((errno == EAGAIN) || (errno == EINTR)))
            {
               executor->onOutput("", 0);
               continue;
            }
            nxlog_debug_tag(DEBUG_TAG, 6, _T("ProcessExecutor::readOutput(): stopped on read (rc=%d err=%s)"), rc, _tcserror(errno));
            break;
         }
      }
      else if (rc == 0)
      {
         // Send empty output on timeout
         executor->onOutput("", 0);
      }
      else
      {
         nxlog_debug_tag(DEBUG_TAG, 6, _T("ProcessExecutor::readOutput(): stopped on poll (%s)"), _tcserror(errno));
         break;
      }
   }
   close(pipe);

#endif

   executor->endOfOutput();
#ifndef _WIN32
   int status;
   waitpid(executor->m_pid, &status, 0);
   if (WIFEXITED(status))
      executor->m_exitCode = WEXITSTATUS(status);
   else
      executor->m_exitCode = -1;

#endif
   executor->m_running = false;
   executor->m_completed.set();
   if (executor->m_selfDestruct)
      delete executor;
}

/**
 * Force stop process
 */
void ProcessExecutor::stop()
{
#ifdef _WIN32
   if (m_phandle != INVALID_HANDLE_VALUE)
      TerminateProcess(m_phandle, 127);
#else
   if (m_pid != 0)
      kill(-m_pid, SIGKILL);  // kill all processes in group
#endif
   waitForCompletion(INFINITE);
   m_started = false;
   m_running = false;
#ifdef _WIN32
   if (m_phandle != INVALID_HANDLE_VALUE)
   {
      CloseHandle(m_phandle);
      m_phandle = INVALID_HANDLE_VALUE;
   }
#else
   m_pid = 0;
#endif
}

/**
 * Perform action when output is generated
 */
void ProcessExecutor::onOutput(const char *text, size_t length)
{
}

/**
 * Perform action after output is generated
 */
void ProcessExecutor::endOfOutput()
{
}

/**
 * Check that process is still running
 */
bool ProcessExecutor::isRunning()
{
   if (!m_running)
      return false;
#ifdef _WIN32
   if (m_phandle == INVALID_HANDLE_VALUE)
      return false;
   DWORD exitCode;
   if (!GetExitCodeProcess(m_phandle, &exitCode))
      return false;
   return exitCode == STILL_ACTIVE;
#else
   return kill(m_pid, 0) == 0;
#endif
}

/**
 * Wait for process completion
 */
bool ProcessExecutor::waitForCompletion(uint32_t timeout)
{
   if (!m_running)
      return true;

#ifdef _WIN32
   if (m_sendOutput)
      return m_completed.wait(timeout);
   return (m_phandle != INVALID_HANDLE_VALUE) ? (WaitForSingleObject(m_phandle, timeout) == WAIT_OBJECT_0) : true;
#else
   return m_completed.wait(timeout);
#endif
}

#ifdef _WIN32

/**
 * Detach from process (Windows only)
 */
void ProcessExecutor::detach()
{
   if (!m_running || m_sendOutput)
      return;

   CloseHandle(m_phandle);
   m_phandle = INVALID_HANDLE_VALUE;
   m_started = false;
   m_running = false;
}

#endif

/**
 * Execute command and forget
 */
bool ProcessExecutor::execute(const TCHAR *cmdLine, bool shellExec)
{
#ifdef _WIN32
   ProcessExecutor executor(cmdLine, shellExec);
   if (!executor.execute())
      return false;
   executor.detach();
   return true;
#else
   auto executor = new ProcessExecutor(cmdLine, shellExec, true);
   if (executor->execute())
      return true;   // Executor object will be destroyed by wait thread
   delete executor;
   return false;
#endif
}
