%option noyywrap
%option reentrant
%option bison-bridge
%option never-interactive

%x COMMENT
%x STR
%x STR2
%x TXT

whitespace	[[:blank:]\t\r]+
identifier	[A-Za-z_\$][A-Za-z_\$0-9]*
compound_ident	{identifier}(::{identifier})+
integer		([0-9]+|0x[0-9A-Fa-f]+)
uint32		{integer}U
int64		{integer}L
uint64		{integer}(UL|LU)
real		[0-9]+\.[0-9]+
escape		\\[bnrt0\"\\]

%{

#ifdef _WIN32
#define YY_NO_UNISTD_H
#pragma warning(disable : 4244 4267)
#define _CRT_SECURE_NO_WARNINGS
#endif

#ifdef __HP_aCC
#pragma diag_suppress 2111, 2177
#endif

#define YY_EXTRA_TYPE	NXSL_Lexer*
#define YY_INPUT(buf, result, maxSize) { result = yyextra->lexerInput(buf, maxSize); }

#define YY_NO_UNPUT

#include "libnxsl.h"
#include "parser.tab.hpp"

static void LexerError(const char *pszMsg, yyscan_t scanner);

// prevent flex debug output on stdout
#undef ECHO
#define ECHO

#define LEXER_ERROR	255

static inline int64_t StringToInt64(const char *s)
{
	if (s[0] == '-')
		return ((s[1] == '0') && (s[2] == 'x')) ? -strtoll(&s[3], nullptr, 16) : strtoll(s, nullptr, 10);
   return ((s[0] == '0') && (s[1] == 'x')) ? strtoll(&s[2], nullptr, 16) : strtoll(s, nullptr, 10);
}

static inline uint64_t StringToUInt64(const char *s)
{
   return ((s[0] == '0') && (s[1] == 'x')) ? strtoull(&s[2], nullptr, 16) : strtoull(s, nullptr, 10);
}

static inline uint32_t StringToUInt32(const char *s)
{
   return ((s[0] == '0') && (s[1] == 'x')) ? strtoul(&s[2], nullptr, 16) : strtoul(s, nullptr, 10);
}

%}

%%

<INITIAL>"abort"    return T_ABORT;
<INITIAL>"and"      return T_AND;
<INITIAL>"array"    return T_ARRAY;
<INITIAL>"boolean"  return T_TYPE_BOOLEAN;
<INITIAL>"break"    return T_BREAK;
<INITIAL>"case"     return T_CASE;
<INITIAL>"catch"    return T_CATCH;
<INITIAL>"const"    return T_CONST;
<INITIAL>"continue" return T_CONTINUE;
<INITIAL>"default"  return T_DEFAULT;
<INITIAL>"do"       return T_DO;
<INITIAL>"else"     return T_ELSE;
<INITIAL>"exit"     return T_EXIT;
<INITIAL>"FALSE"    return T_FALSE;
<INITIAL>"false"    return T_FALSE;
<INITIAL>"for"      return T_FOR;
<INITIAL>"foreach"  return T_FOREACH;
<INITIAL>"global"   return T_GLOBAL;
<INITIAL>"if"       return T_IF;
<INITIAL>"ilike"    return T_ILIKE;
<INITIAL>"imatch"   return T_IMATCH;
<INITIAL>"in"       return T_IN;
<INITIAL>"int32"    return T_TYPE_INT32;
<INITIAL>"int64"    return T_TYPE_INT64;
<INITIAL>"like"     return T_LIKE;
<INITIAL>"match"    return T_MATCH;
<INITIAL>"new"      return T_NEW;
<INITIAL>"not"      return T_NOT;
<INITIAL>"NULL"     return T_NULL;
<INITIAL>"null"     return T_NULL;
<INITIAL>"or"       return T_OR;
<INITIAL>"real"     return T_TYPE_REAL;
<INITIAL>"return"   return T_RETURN;
<INITIAL>"select"   return T_SELECT;
<INITIAL>"string"   return T_TYPE_STRING;
<INITIAL>"sub"      return T_SUB;
<INITIAL>"switch"   return T_SWITCH;
<INITIAL>"TRUE"     return T_TRUE;
<INITIAL>"true"     return T_TRUE;
<INITIAL>"try"      return T_TRY;
<INITIAL>"uint32"   return T_TYPE_UINT32;
<INITIAL>"uint64"   return T_TYPE_UINT64;
<INITIAL>"use"      return T_USE;
<INITIAL>"when"     return T_WHEN;
<INITIAL>"while"    return T_WHILE;
<INITIAL>"with"     return T_WITH;

<INITIAL>{identifier}	{ strlcpy(yylval->valIdentifier.v, yytext, MAX_IDENTIFIER_LENGTH); return T_IDENTIFIER; }
<INITIAL>{compound_ident} { strlcpy(yylval->valIdentifier.v, yytext, MAX_IDENTIFIER_LENGTH); return T_COMPOUND_IDENTIFIER; }
<INITIAL>{integer}	{
				int64_t nVal = StringToInt64(yytext);
				if ((nVal < -2147483647) || (nVal > 2147483647))
				{
					yylval->valInt64 = nVal;
					return T_INT64;
				}
				else
				{
					yylval->valInt32 = static_cast<int32_t>(nVal);
					return T_INT32;
				}
			}
<INITIAL>{uint32} { yylval->valUInt32 = StringToUInt32(yytext); return T_UINT32; }
<INITIAL>{int64}  { yylval->valInt64 = StringToInt64(yytext); return T_INT64; }
<INITIAL>{uint64} { yylval->valUInt64 = StringToUInt64(yytext); return T_UINT64; }
<INITIAL>{real}   { yylval->valReal = strtod(yytext, nullptr); return T_REAL; }

<INITIAL>"=="	return T_EQ;
<INITIAL>"!="	return T_NE;
<INITIAL>"<="	return T_LE;
<INITIAL>">="	return T_GE;
<INITIAL>"<<"	return T_LSHIFT;
<INITIAL>">>"	return T_RSHIFT;
<INITIAL>"&&"	return T_AND;
<INITIAL>"||"	return T_OR;
<INITIAL>"++"	return T_INC;
<INITIAL>"--"	return T_DEC;
<INITIAL>"->"	return T_REF;
<INITIAL>"~="	return T_MATCH;
<INITIAL>"+="	return T_ASSIGN_ADD;
<INITIAL>"-="	return T_ASSIGN_SUB;
<INITIAL>"*="	return T_ASSIGN_MUL;
<INITIAL>"/="	return T_ASSIGN_DIV;
<INITIAL>"\\="	return T_ASSIGN_IDIV;
<INITIAL>"%="	return T_ASSIGN_REM;
<INITIAL>".="	return T_ASSIGN_CONCAT;
<INITIAL>"&="	return T_ASSIGN_AND;
<INITIAL>"|="	return T_ASSIGN_OR;
<INITIAL>"^="	return T_ASSIGN_XOR;
<INITIAL>"..."	return T_RANGE;
<INITIAL>"."	return '.';
<INITIAL>"{"	return '{';
<INITIAL>"}"	return '}';
<INITIAL>"("	return '(';
<INITIAL>")"	return ')';
<INITIAL>";"	return ';';
<INITIAL>":"	return ':';
<INITIAL>"+"	return '+';
<INITIAL>"-"	return '-';
<INITIAL>"*"	return '*';
<INITIAL>"/"	return '/';
<INITIAL>"%"	return '%';
<INITIAL>"="	return '=';
<INITIAL>"!"	return T_NOT;
<INITIAL>"&"	return '&';
<INITIAL>"|"	return '|';
<INITIAL>"^"	return '^';
<INITIAL>"~"	return '~';
<INITIAL>","	return ',';
<INITIAL>"<"	return '<';
<INITIAL>">"	return '>';
<INITIAL>"?"	return '?';
<INITIAL>"["	return '[';
<INITIAL>"]"	return ']';
<INITIAL>"@"	return '@';
<INITIAL>"#"	return '#';
<INITIAL>"\\"	return T_IDIV;

<INITIAL>"/*"     { yyextra->m_commentLevel = 1; BEGIN COMMENT; }
<INITIAL>"\"\"\"" { yyextra->m_text.clear(); BEGIN TXT; }
<INITIAL>\"       { yyextra->m_stringSize = 0; BEGIN STR; }
<INITIAL>\'       { yyextra->m_stringSize = 0; BEGIN STR2; }

<INITIAL>"//".*	;

<INITIAL>\n	yyextra->m_currLine++;
<INITIAL>{whitespace}	;
<INITIAL>.	{
			char szBuffer[128];
#if defined(_WIN32) && (_MSC_VER >= 1400)
			_snprintf_s(szBuffer, 128, _TRUNCATE, "Invalid character '%c' (0x%02X)", *yytext, *yytext);
#else
			snprintf(szBuffer, 128, "Invalid character '%c' (0x%02X)", *yytext, *yytext);
#endif
			LexerError(szBuffer, yyscanner);
			return LEXER_ERROR;
		}

<STR>{escape}	{
			if (yyextra->m_stringSize < MAX_STRING_SIZE)
			{
				switch(yytext[1])
				{
					case '"':
						yyextra->m_string[yyextra->m_stringSize++] = '"';
						break;
					case 'n':
						yyextra->m_string[yyextra->m_stringSize++] = '\n';
						break;
					case 'r':
						yyextra->m_string[yyextra->m_stringSize++] = '\r';
						break;
					case 'b':
						yyextra->m_string[yyextra->m_stringSize++] = '\b';
						break;
					case 't':
						yyextra->m_string[yyextra->m_stringSize++] = '\t';
						break;
					case '0':
						yyextra->m_string[yyextra->m_stringSize++] = 0;
						break;
					default:
						yyextra->m_string[yyextra->m_stringSize++] = yytext[1];
						break;
				}
			}
			else
			{
				LexerError("String constant is too long", yyscanner);
				return LEXER_ERROR;
			}
		}
<STR>\\x[0-9A-Fa-f][0-9A-Fa-f]? {
			char *eptr;
			int nVal;

			nVal = strtol(&yytext[2], &eptr, 16);
			if (*eptr == 0)
			{
				if ((nVal >= 0) && (nVal <= 255))
				{
					yyextra->m_string[yyextra->m_stringSize++] = nVal;
				}
				else
				{
					LexerError("Character code must be in range 0x00 .. 0xFF", yyscanner);
					return LEXER_ERROR;
				}
			}
			else
			{
				LexerError("Invalid hex number", yyscanner);
				return LEXER_ERROR;
			}
		}
<STR>\"		{
			BEGIN INITIAL;
			yyextra->m_string[yyextra->m_stringSize] = 0;
			yylval->valStr = MemCopyStringA(yyextra->m_string);
			return T_STRING;
		}
<STR>\r		;
<STR>\n		{
			LexerError("Newline in string constant", yyscanner);
			return LEXER_ERROR;
		}
<STR>.		{
			if (yyextra->m_stringSize < MAX_STRING_SIZE)
			{
				yyextra->m_string[yyextra->m_stringSize++] = *yytext;
			}
			else
			{
				LexerError("String constant is too long", yyscanner);
				return LEXER_ERROR;
			}
		}

<STR2>\'		{
			BEGIN INITIAL;
			yyextra->m_string[yyextra->m_stringSize] = 0;
			yylval->valStr = MemCopyStringA(yyextra->m_string);
			return T_STRING;
		}
<STR2>\r		;
<STR2>\n		{
			LexerError("Newline in string constant", yyscanner);
			return LEXER_ERROR;
		}
<STR2>.		{
			if (yyextra->m_stringSize < MAX_STRING_SIZE)
			{
				yyextra->m_string[yyextra->m_stringSize++] = *yytext;
			}
			else
			{
				LexerError("String constant is too long", yyscanner);
				return LEXER_ERROR;
			}
		}

<TXT>"\"\"\""		{
			BEGIN INITIAL;
			yyextra->m_text.write(static_cast<char>(0));
			yylval->valStr = MemCopyStringA(reinterpret_cast<const char*>(yyextra->m_text.buffer()));
			return T_STRING;
		}
<TXT>\r		;
<TXT>\n		{ yyextra->m_text.write('\n'); yyextra->m_currLine++; }
<TXT>.		{ yyextra->m_text.write(*yytext); }

<COMMENT>"/*"	yyextra->m_commentLevel++;
<COMMENT>"*/"	{ yyextra->m_commentLevel--; if (yyextra->m_commentLevel == 0) BEGIN INITIAL; }
<COMMENT>\n	yyextra->m_currLine++;
<COMMENT>.	;

%%

static void LexerError(const char *message, yyscan_t scanner)
{
   yyget_extra(scanner)->error(message);
}
