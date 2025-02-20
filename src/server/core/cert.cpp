/* 
** NetXMS - Network Management System
** Copyright (C) 2007-2022 Raden Solutions
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
** File: cert.cpp
**
**/

#include "nxcore.h"
#include <nxcrypto.h>
#include <nxstat.h>

#define DEBUG_TAG    _T("crypto.cert")

#ifdef _WITH_ENCRYPTION

// WARNING! this hack works only for d2i_X509(); be careful when adding new code
#ifdef OPENSSL_CONST
# undef OPENSSL_CONST
#endif
#if OPENSSL_VERSION_NUMBER >= 0x0090800fL
# define OPENSSL_CONST const
#else
# define OPENSSL_CONST
#endif

#if OPENSSL_VERSION_NUMBER < 0x10000000L
static inline int EVP_PKEY_id(EVP_PKEY *key)
{
   return key->type;
}
#endif

#if OPENSSL_VERSION_NUMBER < 0x10100000L
static inline ASN1_TIME *X509_getm_notBefore(const X509 *x)
{
   return X509_get_notBefore(x);
}

static inline ASN1_TIME *X509_getm_notAfter(const X509 *x)
{
   return X509_get_notAfter(x);
}

static inline const ASN1_TIME *X509_get0_notAfter(const X509 *x)
{
   return X509_get_notAfter(x);
}
#endif

/**
 * Server certificate file information
 */
StringSet g_trustedCertificates;
StringSet g_crlList;
TCHAR g_serverCertificatePath[MAX_PATH] = _T("");
TCHAR g_serverCertificateKeyPath[MAX_PATH] = _T("");
char g_serverCertificatePassword[MAX_PASSWORD] = "";
TCHAR g_internalCACertificatePath[MAX_PATH] = _T("");
TCHAR g_internalCACertificateKeyPath[MAX_PATH] = _T("");
char g_internalCACertificatePassword[MAX_PASSWORD] = "";

/**
 * Server certificate
 */
static X509 *s_serverCertificate = nullptr;
static EVP_PKEY *s_serverCertificateKey = nullptr;

/**
 * Server internal certificate
 */
static X509 *s_internalCACertificate = nullptr;
static EVP_PKEY *s_internalCACertificateKey = nullptr;

/**
 * Trusted CA certificate store
 */
static X509_STORE *s_trustedCertificateStore = nullptr;
static Mutex s_certificateStoreLock;

/**
 * Log record ID
 */
static VolatileCounter s_logRecordId = 0;

/**
 * Issue certificate signed with server's certificate
 */
X509 *IssueCertificate(X509_REQ *request, const char *ou, const char *cn, int days)
{
   nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: new certificate request (CN override: %hs, OU override: %hs)"),
            (cn != nullptr) ? cn : "<not set>", (ou != nullptr) ? ou : "<not set>");


   if (s_internalCACertificate == nullptr || s_internalCACertificateKey == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: CA certificate not set"));
      return nullptr;
   }

   X509_NAME *requestSubject = X509_REQ_get_subject_name(request);
   if (requestSubject == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: cannot get subject from certificate request"));
      return nullptr;
   }

   X509 *cert = X509_new();
   if (cert == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: call to X509_new() failed"));
      return nullptr;
   }

   if (X509_set_version(cert, 2) != 1)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: call to X509_set_version() failed"));
      X509_free(cert);
      return nullptr;
   }

#if OPENSSL_VERSION_NUMBER >= 0x10100000L
   ASN1_INTEGER *serial = ASN1_INTEGER_new();
#else
   ASN1_INTEGER *serial = M_ASN1_INTEGER_new();
#endif
   ASN1_INTEGER_set(serial, 0);
   int rc = X509_set_serialNumber(cert, serial);
#if OPENSSL_VERSION_NUMBER >= 0x10100000L
   ASN1_INTEGER_free(serial);
#else
   M_ASN1_INTEGER_free(serial);
#endif
   if (rc != 1)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: cannot set certificate serial number"));
      X509_free(cert);
      return nullptr;
   }

   X509_NAME *subject;
   if ((cn != nullptr) || (ou != nullptr))
   {
      subject = X509_NAME_dup(requestSubject);
      if (subject != nullptr)
      {
         if (ou != nullptr)
         {
            int idx = X509_NAME_get_index_by_NID(subject, NID_organizationalUnitName, -1);
            if (idx != -1)
               X509_NAME_delete_entry(subject, idx);
            if (!X509_NAME_add_entry_by_txt(subject, "OU", MBSTRING_UTF8, (const BYTE *)ou, -1, -1, 0))
               nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: X509_NAME_add_entry_by_txt failed for OU=%hs"), ou);
         }
         if (cn != nullptr)
         {
            int idx = X509_NAME_get_index_by_NID(subject, NID_commonName, -1);
            if (idx != -1)
               X509_NAME_delete_entry(subject, idx);
            if (!X509_NAME_add_entry_by_txt(subject, "CN", MBSTRING_UTF8, (const BYTE *)cn, -1, -1, 0))
               nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: X509_NAME_add_entry_by_txt failed for CN=%hs"), cn);
         }
      }
      else
      {
         nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: call to X509_NAME_dup() failed"));
      }
   }
   else
   {
      subject = requestSubject;
   }

   if (subject == nullptr)
   {
      X509_free(cert);
      return nullptr;
   }

   rc = X509_set_subject_name(cert, subject);
   if (subject != requestSubject)
      X509_NAME_free(subject);
   if (rc != 1)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: call to X509_set_subject_name() failed"));
      X509_free(cert);
      return nullptr;
   }

   X509_NAME *issuerName = X509_get_subject_name(s_internalCACertificate);
   if (issuerName == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: cannot get CA subject name"));
      X509_free(cert);
      return nullptr;
   }

   if (X509_set_issuer_name(cert, issuerName) != 1)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: call to X509_set_issuer_name() failed"));
      X509_free(cert);
      return nullptr;
   }

   EVP_PKEY *pubkey = X509_REQ_get_pubkey(request);
   if (pubkey == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: call to X509_REQ_get_pubkey() failed"));
      X509_free(cert);
      return nullptr;
   }

   if (X509_REQ_verify(request, pubkey) != 1)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: certificate request verification failed"));
      EVP_PKEY_free(pubkey);
      X509_free(cert);
      return nullptr;
   }

   rc = X509_set_pubkey(cert, pubkey);
   EVP_PKEY_free(pubkey);
   if (rc != 1)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: call to X509_set_pubkey() failed"));
      X509_free(cert);
      return nullptr;
   }

   if (X509_gmtime_adj(X509_getm_notBefore(cert), 0) == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: cannot set start time"));
      X509_free(cert);
      return nullptr;
   }

   if (X509_gmtime_adj(X509_getm_notAfter(cert), days * 86400) == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: cannot set end time"));
      X509_free(cert);
      return nullptr;
   }

   if (X509_sign(cert, s_internalCACertificateKey, EVP_sha256()) == 0)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: call to X509_sign() failed"));
      X509_free(cert);
      return nullptr;
   }

   char subjectName[1024];
   X509_NAME_oneline(X509_get_subject_name(cert), subjectName, 1024);
   nxlog_debug_tag(DEBUG_TAG, 4, _T("IssueCertificate: new certificate with subject \"%hs\" issued successfully"), subjectName);
   return cert;
}

/**
 * Get country name from server certificate
 */
bool GetServerCertificateCountry(TCHAR *buffer, size_t size)
{
   if (s_serverCertificate == nullptr)
      return false;
   return GetCertificateSubjectField(s_serverCertificate, NID_countryName, buffer, size);
}

/**
 * Get organization name from server certificate
 */
bool GetServerCertificateOrganization(TCHAR *buffer, size_t size)
{
   if (s_serverCertificate == nullptr)
      return false;
   return GetCertificateSubjectField(s_serverCertificate, NID_organizationName, buffer, size);
}

/**
 * Create X509 certificate structure from login message
 */
X509 *CertificateFromLoginMessage(const NXCPMessage& msg)
{
	X509 *cert = nullptr;
   size_t len;
   const BYTE *data = msg.getBinaryFieldPtr(VID_CERTIFICATE, &len);
	if ((data != nullptr) && (len > 0))
	{
      OPENSSL_CONST BYTE *p = (OPENSSL_CONST BYTE *)data;
		cert = d2i_X509(nullptr, &p, (long)len);
	}
	return cert;
}

/**
 * Check public key
 */
static bool CheckPublicKey(EVP_PKEY *key, const TCHAR *mappingData)
{
	int pkeyLen = i2d_PublicKey(key, nullptr);
	auto ucBuf = MemAllocArray<unsigned char>(pkeyLen + 1);
	auto uctempBuf = ucBuf;
	i2d_PublicKey(key, &uctempBuf);
	
	TCHAR *pkeyText = MemAllocString(pkeyLen * 2 + 1);
	BinToStr(ucBuf, pkeyLen, pkeyText);

	bool valid = (_tcscmp(pkeyText, mappingData) == 0);

	MemFree(ucBuf);
	MemFree(pkeyText);

	return valid;
}

/**
 * Check certificate's CN
 */
static bool CheckCommonName(X509 *cert, const TCHAR *cn)
{
   TCHAR certCn[256];
   if (!GetCertificateCN(cert, certCn, 256))
      return false;

   nxlog_debug_tag(DEBUG_TAG, 3, _T("Certificate CN=\"%s\", user CN=\"%s\""), certCn, cn);
   return _tcsicmp(certCn, cn) == 0;
}

/**
 * Check certificate's template ID
 */
static bool CheckTemplateId(X509 *cert, const TCHAR *userTemplateId)
{
   String certTemplateId = GetCertificateTemplateId(cert);
   if (certTemplateId.isEmpty())
      return false;
   nxlog_debug_tag(DEBUG_TAG, 3, _T("Certificate templateId=\"%s\", user templateId=\"%s\""), certTemplateId.cstr(), userTemplateId);
   return _tcscmp(certTemplateId, userTemplateId) == 0;
}

/**
 * Validate user's certificate
 */
bool ValidateUserCertificate(X509 *cert, const TCHAR *login, const BYTE *challenge, const BYTE *signature, size_t sigLen, CertificateMappingMethod mappingMethod, const TCHAR *mappingData)
{
   bool bValid = false;

   String certSubject = GetCertificateSubjectString(cert);

   nxlog_debug_tag(DEBUG_TAG, 3, _T("Validating certificate \"%s\" for user %s"), certSubject.cstr(), login);
	s_certificateStoreLock.lock();

	if (s_trustedCertificateStore == nullptr)
	{
	   nxlog_debug_tag(DEBUG_TAG, 3, _T("Cannot validate user certificate because certificate store is not initialized"));
		s_certificateStoreLock.unlock();
		return false;
	}

	// Validate signature
#if OPENSSL_VERSION_NUMBER >= 0x10100000L
   EVP_PKEY *pKey = X509_get0_pubkey(cert);
#else
	EVP_PKEY *pKey = X509_get_pubkey(cert);
#endif
	if (pKey != nullptr)
	{
      BYTE hash[SHA1_DIGEST_SIZE];
		CalculateSHA1Hash(challenge, CLIENT_CHALLENGE_SIZE, hash);
		switch(EVP_PKEY_id(pKey))
		{
			case EVP_PKEY_RSA:
				bValid = (RSA_verify(NID_sha1, hash, SHA1_DIGEST_SIZE, const_cast<unsigned char*>(signature), static_cast<unsigned int>(sigLen), EVP_PKEY_get1_RSA(pKey)) != 0);
				break;
			default:
			   nxlog_debug_tag(DEBUG_TAG, 3, _T("Unknown key type %d in certificate \"%s\" for user %s"), EVP_PKEY_id(pKey), certSubject.cstr(), login);
				break;
		}
	}

	// Validate certificate
	if (bValid)
	{
		X509_STORE_CTX *pStore = X509_STORE_CTX_new();
		if (pStore != nullptr)
		{
			X509_STORE_CTX_init(pStore, s_trustedCertificateStore, cert, nullptr);
			bValid = (X509_verify_cert(pStore) != 0);
			X509_STORE_CTX_free(pStore);
			nxlog_debug_tag(DEBUG_TAG, 3, _T("Certificate \"%s\" for user %s - validation %s"), certSubject.cstr(), login, bValid ? _T("successful") : _T("failed"));
		}
		else
		{
			TCHAR szBuffer[256];
			nxlog_debug_tag(DEBUG_TAG, 3, _T("X509_STORE_CTX_new() failed: %s"), _ERR_error_tstring(ERR_get_error(), szBuffer));
			bValid = false;
		}
	}

	// Check user mapping
	if (bValid)
	{
		switch(mappingMethod)
		{
			case MAP_CERTIFICATE_BY_SUBJECT:
				bValid = (_tcsicmp(certSubject, CHECK_NULL_EX(mappingData)) == 0);
				break;
			case MAP_CERTIFICATE_BY_PUBKEY:
				bValid = CheckPublicKey(pKey, CHECK_NULL_EX(mappingData));
				break;
			case MAP_CERTIFICATE_BY_CN:
            bValid = CheckCommonName(cert, ((mappingData != nullptr) && (*mappingData != 0)) ? mappingData : login);
				break;
         case MAP_CERTIFICATE_BY_TEMPLATE_ID:
            bValid = CheckTemplateId(cert, CHECK_NULL_EX(mappingData));;
            break;
			default:
			   nxlog_debug_tag(DEBUG_TAG, 3, _T("Invalid certificate mapping method %d for user %s"), mappingMethod, login);
				bValid = false;
				break;
		}
	}

#if OPENSSL_VERSION_NUMBER < 0x10100000L
	if (pKey != nullptr)
	   EVP_PKEY_free(pKey);
#endif

	s_certificateStoreLock.unlock();
	return bValid;
}

/**
 * Reload certificates from database
 */
void ReloadCertificates()
{
   auto it = g_crlList.begin();
   while(it.hasNext())
   {
      const TCHAR *location = it.next();
      if (!_tcsncmp(location, _T("http://"), 7) || !_tcsncmp(location, _T("https://"), 8))
      {
#ifdef UNICODE
         char *url = UTF8StringFromWideString(location);
         AddRemoteCRL(url, true);
         MemFree(url);
#else
         AddRemoteCRL(location, true);
#endif
      }
      else
      {
         AddLocalCRL(location);
      }
   }

	s_certificateStoreLock.lock();

	if (s_trustedCertificateStore != nullptr)
		X509_STORE_free(s_trustedCertificateStore);

	s_trustedCertificateStore = CreateTrustedCertificatesStore(g_trustedCertificates, true);
	if (s_trustedCertificateStore != nullptr)
	{
	   // Add internal CA certificate as trusted
	   if (s_internalCACertificate != nullptr)
         X509_STORE_add_cert(s_trustedCertificateStore, s_internalCACertificate);
	}
	else
	{
	   TCHAR buffer[256];
		nxlog_write_tag(NXLOG_ERROR, DEBUG_TAG, _T("Cannot initialize certificate store (%s)"), _ERR_error_tstring(ERR_get_error(), buffer));
	}

	s_certificateStoreLock.unlock();
}

/**
 * Certificate stuff initialization
 */
void InitCertificates()
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   DB_RESULT hResult = DBSelect(hdb, _T("SELECT max(record_id) FROM certificate_action_log"));
   if (hResult != nullptr)
   {
      if (DBGetNumRows(hResult) > 0)
         s_logRecordId = DBGetFieldLong(hResult, 0, 0);
      DBFreeResult(hResult);
   }
   DBConnectionPoolReleaseConnection(hdb);

   ReloadCertificates();
}

/**
 * Load certificate
 */
static bool LoadCertificate(RSA **serverKey, const TCHAR *certificatePath, const TCHAR *certificateKeyPath, char *certificatePassword, X509 **certificate, EVP_PKEY **certificateKey, const TCHAR *certType)
{
   if (certificatePath[0] == 0)
   {
      nxlog_write_tag(NXLOG_INFO, DEBUG_TAG, _T("%s certificate not set"), certType);
      return false;
   }

   // Load server certificate and private key
   FILE *f = _tfopen(certificatePath, _T("r"));
   if (f == nullptr)
   {
      nxlog_write_tag(NXLOG_ERROR, DEBUG_TAG, _T("Cannot load %s certificate from %s (%s)"), certType, certificatePath, _tcserror(errno));
      return false;
   }

   DecryptPasswordA("system", certificatePassword, certificatePassword, MAX_PASSWORD);
   *certificate = PEM_read_X509(f, nullptr, nullptr, certificatePassword);
   if (certificateKeyPath[0] != 0)
   {
      // Server key is in separate file
      fclose(f);
      f = _tfopen(certificateKeyPath, _T("r"));
      if (f == nullptr)
      {
         nxlog_write(NXLOG_ERROR, _T("Cannot load %s certificate key from %s (%s)"), certType, certificateKeyPath, _tcserror(errno));
         return false;
      }
   }
   *certificateKey = PEM_read_PrivateKey(f, nullptr, nullptr, certificatePassword);
   fclose(f);

   if ((*certificate == nullptr) || (*certificateKey == nullptr))
   {
      TCHAR buffer[1024];
      nxlog_write(NXLOG_ERROR, _T("Cannot load %s certificate from %s (%s)"), certType, certificatePath, _ERR_error_tstring(ERR_get_error(), buffer));
      return false;
   }
   nxlog_debug_tag(DEBUG_TAG, 3, _T("%s certificate: %s"), certType, static_cast<const TCHAR*>(GetCertificateSubjectString(*certificate)));

   if (serverKey != nullptr)
   {
      RSA *privKey = EVP_PKEY_get1_RSA(*certificateKey);
      RSA *pubKey = EVP_PKEY_get1_RSA(X509_get_pubkey(*certificate));
      if ((privKey != nullptr) && (pubKey != nullptr))
      {
         // Combine into one key
         int len = i2d_RSAPublicKey(pubKey, nullptr);
         len += i2d_RSAPrivateKey(privKey, nullptr);
         BYTE *buffer = MemAllocArray<BYTE>(len);

         BYTE *pos = buffer;
         i2d_RSAPublicKey(pubKey, &pos);
         i2d_RSAPrivateKey(privKey, &pos);

         *serverKey = RSAKeyFromData(buffer, len, true);
         MemFree(buffer);
      }
   }

   return true;
}

/**
 * Load server certificate
 */
bool LoadServerCertificate(RSA **serverKey)
{
   return LoadCertificate(serverKey, g_serverCertificatePath, g_serverCertificateKeyPath, g_serverCertificatePassword, &s_serverCertificate, &s_serverCertificateKey, _T("Server"));
}

/**
 * Load CA certificate
 */
bool LoadInternalCACertificate()
{
   return LoadCertificate(nullptr, g_internalCACertificatePath, g_internalCACertificateKeyPath, g_internalCACertificatePassword, &s_internalCACertificate, &s_internalCACertificateKey, _T("Internal CA"));
}

#if HAVE_X509_STORE_SET_VERIFY_CB

/**
 * Certificate verification callback
 */
static int CertVerifyCallback(int success, X509_STORE_CTX *ctx)
{
   if (!success)
   {
      X509 *cert = X509_STORE_CTX_get_current_cert(ctx);
      int error = X509_STORE_CTX_get_error(ctx);
      int depth = X509_STORE_CTX_get_error_depth(ctx);
      char subjectName[1024];
      X509_NAME_oneline(X509_get_subject_name(cert), subjectName, 1024);
      nxlog_debug_tag(DEBUG_TAG, 4, _T("Certificate \"%hs\" verification error %d (%hs) at depth %d"),
             subjectName, error, X509_verify_cert_error_string(error), depth);
   }
   return success;
}

#endif /* HAVE_X509_STORE_SET_VERIFY_CB */

/**
 * Setup server-side TLS context
 */
bool SetupServerTlsContext(SSL_CTX *context)
{
   if ((s_serverCertificate == nullptr) || (s_serverCertificateKey == nullptr))
      return false;

   X509_STORE *store = CreateTrustedCertificatesStore(g_trustedCertificates, true);
   if (store == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 3, _T("SetupServerTlsContext: cannot create certificate store"));
      return false;
   }
#if HAVE_X509_STORE_SET_VERIFY_CB
   X509_STORE_set_verify_cb(store, CertVerifyCallback);
#endif
   if (s_internalCACertificate != nullptr)
      X509_STORE_add_cert(store, s_internalCACertificate);

   SSL_CTX_set_cert_store(context, store);
   SSL_CTX_use_certificate(context, s_serverCertificate);
   SSL_CTX_use_PrivateKey(context, s_serverCertificateKey);
   SSL_CTX_set_verify(context, SSL_VERIFY_PEER | SSL_VERIFY_CLIENT_ONCE, nullptr);
   return true;
}

/**
 * Scheduled task for CRL reloading
 */
void ReloadCRLs(const shared_ptr<ScheduledTaskParameters>& parameters)
{
   nxlog_debug_tag(DEBUG_TAG, 2, _T("Reloading all registered CRLs"));
   ReloadAllCRLs();
}

/**
 * Log certificate action
 */
void LogCertificateAction(CertificateOperation operation, UINT32 userId, UINT32 nodeId, const uuid& nodeGuid, CertificateType type, X509 *cert)
{
   ASN1_INTEGER *serial = X509_get_serialNumber(cert);
   LogCertificateAction(operation, userId, nodeId, nodeGuid, type, GetCertificateSubjectString(cert), ASN1_INTEGER_get(serial));
}

/**
 * Get expiration date for server certificate
 */
String GetServerCertificateExpirationDate()
{
   if (s_serverCertificate == nullptr)
      return String();

   time_t e = GetCertificateExpirationTime(s_serverCertificate);
   TCHAR buffer[64];
   _tcsftime(buffer, 64, _T("%Y-%m-%d"), localtime(&e));
   return String(buffer);
}

/**
 * Get number of days until server certificate expiration
 */
int GetServerCertificateDaysUntilExpiration()
{
   if (s_serverCertificate == nullptr)
      return -1;

   time_t e = GetCertificateExpirationTime(s_serverCertificate);
   time_t now = time(nullptr);
   return static_cast<int>((e - now) / 86400);
}

/**
 * Get server certificate expiration time
 */
time_t GetServerCertificateExpirationTime()
{
   if (s_serverCertificate == nullptr)
      return 0;

   return GetCertificateExpirationTime(s_serverCertificate);
}

#else		/* _WITH_ENCRYPTION */

/**
 * Stub for certificate initialization
 */
void InitCertificates()
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   DB_RESULT hResult = DBSelect(hdb, _T("SELECT max(record_id) FROM certificate_action_log"));
   if (hResult != nullptr)
   {
      if (DBGetNumRows(hResult) > 0)
         s_logRecordId = DBGetFieldLong(hResult, 0, 0);
      DBFreeResult(hResult);
   }
   DBConnectionPoolReleaseConnection(hdb);
}

/**
 * Get expiration date for server certificate
 */
String GetServerCertificateExpirationDate()
{
   return String();
}

/**
 * Get number of days until server certificate expiration
 */
int GetServerCertificateDaysUntilExpiration()
{
   return -1;
}

/**
 * Get server certificate expiration time
 */
time_t GetServerCertificateExpirationTime()
{
   return 0;
}

#endif	/* _WITH_ENCRYPTION */

/**
 * Log certificate action
 */
void LogCertificateAction(CertificateOperation operation, UINT32 userId, UINT32 nodeId, const uuid& nodeGuid, CertificateType type, const TCHAR *subject, INT32 serial)
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   DB_STATEMENT hStmt = DBPrepare(hdb, _T("INSERT INTO certificate_action_log (record_id,timestamp,operation,user_id,node_id,node_guid,cert_type,subject,serial) VALUES (?,?,?,?,?,?,?,?,?)"));
   if (hStmt != nullptr)
   {
      DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, InterlockedIncrement(&s_logRecordId));
      DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, static_cast<uint32_t>(time(nullptr)));
      DBBind(hStmt, 3, DB_SQLTYPE_INTEGER, static_cast<int32_t>(operation));
      DBBind(hStmt, 4, DB_SQLTYPE_INTEGER, userId);
      DBBind(hStmt, 5, DB_SQLTYPE_INTEGER, nodeId);
      DBBind(hStmt, 6, DB_SQLTYPE_VARCHAR, nodeGuid);
      DBBind(hStmt, 7, DB_SQLTYPE_INTEGER, static_cast<int32_t>(type));
      DBBind(hStmt, 8, DB_SQLTYPE_VARCHAR, subject, DB_BIND_STATIC);
      DBBind(hStmt, 9, DB_SQLTYPE_INTEGER, serial);
      DBExecute(hStmt);
      DBFreeStatement(hStmt);
   }
   DBConnectionPoolReleaseConnection(hdb);
}
