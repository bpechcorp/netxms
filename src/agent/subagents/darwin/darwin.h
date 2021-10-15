/* 
** netxms subagent for darwin
** copyright (c) 2012 alex kirhenshtein
**
** this program is free software; you can redistribute it and/or modify
** it under the terms of the gnu general public license as published by
** the free software foundation; either version 2 of the license, or
** (at your option) any later version.
**
** this program is distributed in the hope that it will be useful,
** but without any warranty; without even the implied warranty of
** merchantability or fitness for a particular purpose.  see the
** gnu general public license for more details.
**
** you should have received a copy of the gnu general public license
** along with this program; if not, write to the free software
** foundation, inc., 675 mass ave, cambridge, ma 02139, usa.
**
**/

#ifndef __darwin_h__

#include <nms_common.h>
#include <nms_agent.h>
#include <nms_util.h>
#include <interface_types.h>

#include <pwd.h>

enum
{
	INTERVAL_1MIN,
	INTERVAL_5MIN,
	INTERVAL_15MIN
};

/**
 * Attributes for H_ProcInfo
 */
enum
{
   PROCINFO_CPUTIME,
   PROCINFO_KTIME,
   PROCINFO_PAGEFAULTS,
   PROCINFO_THREADS,
   PROCINFO_HANDLES,
   PROCINFO_UTIME,
   PROCINFO_VMREGIONS,
   PROCINFO_VMSIZE,
   PROCINFO_WKSET
};

#define INFOTYPE_MIN 0
#define INFOTYPE_MAX 1
#define INFOTYPE_AVG 2
#define INFOTYPE_SUM 3

#define DEBUG_TAG _T("darwin")

#define __darwin_h__

LONG H_ProcessCount(const TCHAR *, const TCHAR *, TCHAR *, AbstractCommSession *);
LONG H_ProcessDetails(const TCHAR *, const TCHAR *, TCHAR *, AbstractCommSession *);
LONG H_ProcessList(const TCHAR *, const TCHAR *, StringList *, AbstractCommSession *);
LONG H_ProcessTable(const TCHAR *cmd, const TCHAR *arg, Table *value, AbstractCommSession *);

#endif // __darwin_h__
