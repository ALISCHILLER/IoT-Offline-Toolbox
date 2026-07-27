#!/usr/bin/env python3
from __future__ import annotations
import re, sys, xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def read(p): return (ROOT/p).read_text()
def req(p,t,m):
    if t not in read(p): errors.append(f"{p}: {m}")
def forbid(p,t,m):
    if t in read(p): errors.append(f"{p}: {m}")
def strings(folder):
    root=ET.parse(ROOT/f"composeApp/src/commonMain/composeResources/{folder}/strings.xml").getroot()
    return {n.attrib['name']:n.text or '' for n in root.findall('string')}
def placeholders(v): return sorted(re.findall(r"%(?:\d+\$)?[a-zA-Z]",v))
def find_ordinary_strings_crossing_lines(text: str) -> list[int]:
    lines = []
    in_string = False
    triple = False
    escaped = False
    start_line = 1
    line = 1
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "\n":
            if in_string and not triple:
                lines.append(start_line)
                in_string = False
                escaped = False
            line += 1
            i += 1
            continue
        if not in_string:
            if text.startswith('"""', i):
                in_string = True
                triple = True
                start_line = line
                i += 3
                continue
            if ch == '"':
                in_string = True
                triple = False
                escaped = False
                start_line = line
        elif triple:
            if text.startswith('"""', i):
                in_string = False
                triple = False
                i += 3
                continue
        elif escaped:
            escaped = False
        elif ch == "\\":
            escaped = True
        elif ch == '"':
            in_string = False
        i += 1
    return lines

build_gradle='composeApp/build.gradle.kts'
for t in ('compose.resources {', 'publicResClass = true', 'packageOfResClass = "com.msa.iotofflinetoolbox.resources"', 'generateResClass = always'):
    req(build_gradle,t,'Compose resource accessor generation is not forced/configured')

en,fa=strings('values'),strings('values-fa')
if len(en)!=len(fa): errors.append(f"resource count mismatch English={len(en)} Persian={len(fa)}")
if set(en)!=set(fa): errors.append('resource keys differ')
for k in set(en)&set(fa):
    if placeholders(en[k])!=placeholders(fa[k]): errors.append(f'placeholder mismatch {k}')
models='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/model/Models.kt'
for f in ('defaultHttpConnectTimeoutMillis','defaultHttpSocketTimeoutMillis','connectTimeoutMillis','socketTimeoutMillis','manualCookieHeader','customWebSocketHeaders','browserManagedRequestHeaders','manualHttpRedirects','publicCleartextOverride','httpConnectTimeout','httpSocketTimeout'): req(models,f,f'missing {f}')
forbid(models,'redactSecretsInLogs','redaction must not be configurable')
client='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HttpToolClient.kt'
for t in ('requestTimeoutMillis = timeoutBudget.requestMillis','connectTimeoutMillis = timeoutBudget.connectMillis','socketTimeoutMillis = timeoutBudget.socketMillis'): req(client,t,'timeout wiring incomplete')
for t in ('forwardsBody','crossesOriginWithSensitiveData','request would forward credentials, cookies or a request body'): req(client,t,'cross-origin redirect data forwarding is not blocked')
validation='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/NetworkValidation.kt'
for t in ('calculateHttpTimeoutBudget','HTTP total request timeout exceeded','remainingReceiveWindowMillis','validateWebSocketHeaders'): req(validation,t,'network validation boundary missing')
preview='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HttpRequestTools.kt'
if read(preview).count('lines += "  --max-time') != 1: errors.append(f'{preview}: cURL preview must contain exactly one --max-time option')
req(preview,'fun isBrowserForbiddenRequestHeader','browser forbidden-header classifier missing')
request_validation='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HttpRequestValidation.kt'
for t in ('object HttpRequestValidator','MAX_REQUEST_BODY_BYTES','DUPLICATE_CONTENT_TYPE','browserManagedRequestHeaders','TransportSecurity.isPublicCleartext'): req(request_validation,t,'central HTTP request validation is incomplete')
response_tools='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HttpResponseBodyTools.kt'
for t in ('object HttpResponseBodyTools','isValidUtf8','toHex','toBase64','isTextualContentType'): req(response_tools,t,'binary-aware HTTP response handling is incomplete')
for t in ('HttpRequestValidator.requireValid','HttpResponseBodyTools.decode','bodyBytes = bodyBytes','bodyIsText = decodedBody.isText'): req(client,t,'HTTP client validation/response-byte handling is incomplete')
settings='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/SettingsScreen.kt'
req(settings,'settings.maxPortScanItems !in 1..4_096','port validation mismatch')
forbid(settings,'settings.maxPortScanItems !in 1..65_535','silent port clamp remains')
http='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/HttpScreen.kt'
for t in ('PasswordVisualTransformation()','Res.string.http_cookie_unsupported','Res.string.http_redirect_unsupported','Res.string.http_browser_forbidden_headers','httpConnectTimeout','httpSocketTimeout'): req(http,t,'HTTP capability/secret UI incomplete')
red='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/data/SecretRedactor.kt'
for t in ('redactCustomSensitiveHeaderLines','sensitiveHeaderTokens','SENSITIVE_NAME','isSensitiveFieldName'): req(red,t,'cross-channel sensitive-field redaction missing')
for source,suffix in (('jsMain','js'),('wasmJsMain','wasmJs')):
    p=f'composeApp/src/{source}/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.{suffix}.kt'
    for t in ('manualCookieHeader = false','customWebSocketHeaders = false','browserManagedRequestHeaders = true','manualHttpRedirects = false','publicCleartextOverride = false','httpConnectTimeout = false','httpSocketTimeout = false'): req(p,t,'browser capability mismatch')
ios='composeApp/src/iosMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.ios.kt'
for t in ('state = PortState.FILTERED','state = PortState.ERROR','publicCleartextOverride = false','httpConnectTimeout = false','httpSocketTimeout = true'): req(ios,t,'iOS capability/port outcome mismatch')
for source,suffix in (('androidMain','android'),('desktopMain','desktop')):
    p=f'composeApp/src/{source}/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.{suffix}.kt'
    req(p,'catch (error: ConnectException)','JVM closed state inferred from text')
    for t in ('operationDeadline','remainingDeadlineMillis','remainingSocketTimeoutMillis'): req(p,t,'discovery timeout is not globally bounded')
    for t in (
        'catch (cancelled: CancellationException)',
        'HttpResponseBodyTools.decode(response, contentType = null).text',
        'useCancellableIo(',
        'continuation.invokeOnCancellation',
        'resource.close()',
    ):
        req(p,t,'JVM exchange cancellation or binary-response safety contract missing')
store='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxStore.kt'
for t in ('HttpRequestValidator.firstIssue','HttpRequestValidationPolicy(','capabilities.customWebSocketHeaders','capabilities.browserManagedRequestHeaders','private fun localized(language: AppLanguage','capabilities.publicCleartextOverride','localizedBackupInspection','localizedSource'): req(store,t,'Store platform/security/localization enforcement missing')
for t in ('private val operationCoordinator = OperationCoordinator(scope)', 'operationCoordinator.cancelCurrent()', 'operationCoordinator.launch('):
    req(store, t, 'Store operation coordinator integration missing')
operation_coordinator='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/OperationCoordinator.kt'
for t in (
    'private var operationTail: Job? = null',
    'val previousJob = operationTail',
    'previousJob?.cancel()',
    'previousJob?.join()',
    'operationTail = job',
    'if (sequence != operationId) return@operation',
    'catch (cancelled: CancellationException)',
):
    req(operation_coordinator, t, 'Operation sequencing/cancellation contract missing')
allkt='\n'.join(p.read_text() for p in (ROOT/'composeApp/src').rglob('*.kt'))
if re.search(r'\btr\s*\(',allkt): errors.append('legacy tr(...) calls remain')
for kotlin_file in ROOT.rglob('*.kt'):
    source = kotlin_file.read_text(encoding='utf-8')
    if source.lstrip().startswith("'private "):
        errors.append(f'{kotlin_file.relative_to(ROOT)}: stray apostrophe before declaration')
    for line in find_ordinary_strings_crossing_lines(source):
        errors.append(f'{kotlin_file.relative_to(ROOT)}:{line}: ordinary string crosses a line boundary')
for t in ('finalUrl = currentUrl', 'redirected = redirectCount > 0'):
    req(client, t, 'HTTP failures after redirects must report the actual final target')
for t in ('val redactedContent = SecretRedactor.redact(template.content)', 'content = redactedContent'):
    req(store, t, 'payload template persistence must redact detected secrets')
mqtt_model='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/model/Models.kt'
for t in ('val binary: Boolean = false', 'val truncated: Boolean = false'):
    req(mqtt_model, t, 'MQTT binary/truncation evidence fields missing')
mqtt_codec='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/MqttCodec.kt'
for t in ('HttpResponseBodyTools.decode(payload, contentType = null)', 'binary = !decoded.isText', 'truncated = truncated'):
    req(mqtt_codec, t, 'MQTT binary payload safety contract missing')
coap_codec='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/CoapCodec.kt'
for t in ('contentTypeForFormat', 'HttpResponseBodyTools.decode(', 'CoapCodec.contentTypeForFormat(contentFormat)'):
    req(coap_codec, t, 'CoAP content-aware payload decoding contract missing')
ios_network_text = read(ios)
forbid(ios, 'mutableListOf<Byte>()', 'iOS bounded TCP reads must not box response bytes in MutableList<Byte>')
for t in ('val captured = ByteArray(captureLimit)', 'buffer.copyInto(', 'capturedSize += accepted'):
    req(ios, t, 'iOS bounded TCP byte-buffer contract missing')
repository='composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/data/ToolboxRepository.kt'
for t in (
    'rollbackImportSnapshot(',
    '"settings" to { saveSettings(settings) }',
    '.forEach { (name, restore) ->',
    'if (runCatching { restore() }.isFailure) failures += name',
    'return failures',
):
    req(repository, t, 'backup-import rollback must attempt every snapshot restore independently')
print(f'Deep quality audit: English={len(en)}, Persian={len(fa)}, errors={len(errors)}')
for e in errors: print('ERROR:',e)
if errors: sys.exit(1)
print('DEEP_QUALITY_AUDIT_PASSED')
