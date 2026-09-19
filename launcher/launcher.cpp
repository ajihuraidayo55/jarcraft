// legacy jarcraft launcher - pure Win32, Win98-style retro UI.
// Detects Java, lets the user pick RAM, launches the jarcraft game JAR
// (flexible lookup: exact name first, then any jarcraft*.jar next to the
// launcher) and streams the game output into a log pane.
//
// Build: x86_64-w64-mingw32-g++-posix -O2 -municode -mwindows -static
//        launcher.cpp resource.o -o "legacy-jarcraft-launcher.exe"

#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <stdarg.h>

// ---------------- IDs ----------------
#define IDC_TITLE        101
#define IDC_GRP_JAVA     110
#define IDC_JAVA_INFO    111
#define IDC_BTN_REFRESH  112
#define IDC_GRP_MEM      120
#define IDC_RB_1G        121
#define IDC_RB_2G        122
#define IDC_RB_4G        123
#define IDC_BTN_PLAY     130
#define IDC_BTN_QUIT     131
#define IDC_GRP_LOG      140
#define IDC_LOG          141
#define IDC_STATUS       150

#define WM_APP_LOG       (WM_APP + 1)
#define WM_APP_DONE      (WM_APP + 2)

// Preferred names in order; after that any jarcraft*.jar is accepted.
static const wchar_t *const JAR_CANDIDATES[] = {
    L"jarcraft alpha1.0.0.jar",
    L"jarcraft-alpha1.0.0.jar",
    L"jarcraft.jar",
    NULL
};

static HWND g_hWnd, g_log, g_javaInfo, g_status, g_play;
static HFONT g_font, g_fontBold;
static wchar_t g_jarPath[MAX_PATH];
static wchar_t g_javaPath[MAX_PATH];
static wchar_t g_javaVer[64];
static BOOL g_javaFound = FALSE;
static BOOL g_running = FALSE;

struct LaunchParams { DWORD memMB; };

// ---------------- jar lookup ----------------
// FindJar: resolve the game JAR path next to the launcher.
// Tries the preferred names first, then falls back to a case-insensitive
// "jarcraft*.jar" scan of the launcher directory, so the game still starts
// even if the jar gets renamed during download or by the user.
static BOOL FindJar(const wchar_t *dir, wchar_t *out, DWORD outChars) {
    wchar_t probe[MAX_PATH];
    // 1) exact candidate names
    for (int i = 0; JAR_CANDIDATES[i]; i++) {
        _snwprintf(probe, MAX_PATH - 1, L"%s%s", dir, JAR_CANDIDATES[i]);
        probe[MAX_PATH - 1] = 0;
        DWORD attr = GetFileAttributesW(probe);
        if (attr != INVALID_FILE_ATTRIBUTES && !(attr & FILE_ATTRIBUTE_DIRECTORY)) {
            _snwprintf(out, outChars - 1, L"%s", probe);
            out[outChars - 1] = 0;
            return TRUE;
        }
    }
    // 2) wildcard scan: jarcraft*.jar (FindFirstFileW matching is case-insensitive)
    _snwprintf(probe, MAX_PATH - 1, L"%sjarcraft*.jar", dir);
    probe[MAX_PATH - 1] = 0;
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(probe, &fd);
    if (h != INVALID_HANDLE_VALUE) {
        _snwprintf(out, outChars - 1, L"%s%s", dir, fd.cFileName);
        out[outChars - 1] = 0;
        FindClose(h);
        return TRUE;
    }
    return FALSE;
}

// ---------------- log helpers ----------------
static void AppendLogW(HWND hwnd, const wchar_t *text) {
    int len = GetWindowTextLengthW(hwnd);
    SendMessageW(hwnd, EM_SETSEL, len, len);
    SendMessageW(hwnd, EM_REPLACESEL, FALSE, (LPARAM)text);
}

static void LogLine(const wchar_t *fmt, ...) {
    wchar_t buf[2048];
    va_list ap;
    va_start(ap, fmt);
    _vsnwprintf(buf, 2040, fmt, ap);
    va_end(ap);
    wcscat(buf, L"\r\n");
    if (g_log) AppendLogW(g_log, buf);
}

// ---------------- java detection ----------------
// Run "<exe> -version" and capture stderr (java prints its version there).
// Returns true and fills verStr ("21.0.4") on success.
static BOOL ProbeJava(const wchar_t *exe, wchar_t *verStr, DWORD verChars) {
    SECURITY_ATTRIBUTES sa = {sizeof(sa), NULL, TRUE};
    HANDLE rd = NULL, wr = NULL;
    if (!CreatePipe(&rd, &wr, &sa, 0)) return FALSE;
    SetHandleInformation(wr, HANDLE_FLAG_INHERIT, HANDLE_FLAG_INHERIT);

    wchar_t cmd[MAX_PATH + 64];
    _snwprintf(cmd, MAX_PATH + 63, L"\"%s\" -version", exe);
    cmd[MAX_PATH + 63] = 0;
    wchar_t cmdMutable[MAX_PATH + 64];
    wcscpy(cmdMutable, cmd);

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = wr;
    si.hStdError = wr;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    ZeroMemory(&pi, sizeof(pi));

    BOOL ok = CreateProcessW(NULL, cmdMutable, NULL, NULL, TRUE,
                             CREATE_NO_WINDOW, NULL, NULL, &si, &pi);
    CloseHandle(wr);
    if (!ok) { CloseHandle(rd); return FALSE; }

    char buf[4096];
    DWORD read = 0, total = 0;
    while (total < sizeof(buf) - 1 &&
           ReadFile(rd, buf + total, sizeof(buf) - 1 - total, &read, NULL) && read > 0) {
        total += read;
    }
    buf[total] = 0;
    CloseHandle(rd);
    WaitForSingleObject(pi.hProcess, 3000);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);

    // parse: version "21.0.4" / "1.8.0_402"
    const char *v = strstr(buf, "version \"");
    if (!v) return FALSE;
    v += 9;
    const char *e = strchr(v, '"');
    if (!e) return FALSE;

    int len = 0;
    wchar_t *out = verStr;
    for (const char *p = v; p < e && len < (int)verChars - 1; p++, len++) {
        out[len] = (wchar_t)(unsigned char)*p;
    }
    out[len] = 0;
    return len > 0;
}

// Major version number from a "21.0.4" / "1.8.0_402" string.
static int JavaMajor(const wchar_t *ver) {
    int a = _wtoi(ver);
    if (a == 1) {                       // legacy "1.8.0_x"
        const wchar_t *dot = wcschr(ver, L'.');
        if (dot) a = _wtoi(dot + 1);
    }
    return a;
}

static void SetJavaCandidate(HWND hwnd, const wchar_t *exe, const wchar_t *ver) {
    wcsncpy(g_javaPath, exe, MAX_PATH - 1);
    g_javaPath[MAX_PATH - 1] = 0;
    wcsncpy(g_javaVer, ver, 63);
    g_javaVer[63] = 0;
    g_javaFound = TRUE;
    EnableWindow(g_play, TRUE);
    SetWindowTextW(g_status, L"Ready.");
    LogLine(L"[launcher] java %s found: %s", ver, exe);
    SetWindowTextW(g_javaInfo, ver);
    wchar_t msg[128];
    _snwprintf(msg, 127, L"Java %s - OK", ver);
    msg[127] = 0;
    SetWindowTextW(g_javaInfo, msg);
}

static void ScanJavaDirs(HWND hwnd, const wchar_t *base, int *bestMajor,
                         wchar_t *bestExe, wchar_t *bestVer);

static void DetectJava(HWND hwnd) {
    g_javaFound = FALSE;
    EnableWindow(g_play, FALSE);
    SetWindowTextW(g_status, L"Scanning for Java...");
    LogLine(L"[launcher] scanning for Java...");

    wchar_t exe[MAX_PATH], ver[64];
    int bestMajor = 0;
    wchar_t bestExe[MAX_PATH] = L"", bestVer[64] = L"";

    // remember candidates; prefer the highest version >= 8
    struct Cand { wchar_t exe[MAX_PATH]; wchar_t ver[64]; int major; };
    // simple approach: track best (highest major), tie-break: later sources
    #define CONSIDER(e, v) do { \
        int mj = JavaMajor(v); \
        if (mj > bestMajor) { bestMajor = mj; wcsncpy(bestExe, e, MAX_PATH-1); bestExe[MAX_PATH-1]=0; \
            wcsncpy(bestVer, v, 63); bestVer[63]=0; } \
    } while (0)

    // 1) PATH
    if (ProbeJava(L"java", ver, 64)) {
        wchar_t *p;
        wchar_t full[MAX_PATH];
        DWORD r = SearchPathW(NULL, L"java", L".exe", MAX_PATH, full, &p);
        if (r > 0 && r < MAX_PATH) CONSIDER(full, ver);
    }
    // 2) JAVA_HOME
    wchar_t jh[MAX_PATH];
    DWORD n = GetEnvironmentVariableW(L"JAVA_HOME", jh, MAX_PATH);
    if (n > 0 && n < MAX_PATH - 16) {
        wcscat(jh, L"\\bin\\java.exe");
        if (ProbeJava(jh, ver, 64)) CONSIDER(jh, ver);
    }
    // 3) common install dirs
    ScanJavaDirs(hwnd, L"C:\\Program Files\\Java", &bestMajor, bestExe, bestVer);
    ScanJavaDirs(hwnd, L"C:\\Program Files\\Eclipse Adoptium", &bestMajor, bestExe, bestVer);
    ScanJavaDirs(hwnd, L"C:\\Program Files (x86)\\Java", &bestMajor, bestExe, bestVer);
    ScanJavaDirs(hwnd, L"C:\\Program Files\\Microsoft", &bestMajor, bestExe, bestVer);

    // re-apply best from dirs (CONSIDER inside ScanJavaDirs already updated)
    if (bestMajor > 0) {
        SetJavaCandidate(hwnd, bestExe, bestVer);
    } else {
        SetWindowTextW(g_javaInfo, L"Java not found on this PC.");
        SetWindowTextW(g_status, L"Java not found - install Java 8 or newer.");
        LogLine(L"[launcher] no suitable Java found (need 8+).");
        LogLine(L"[launcher] get it free at: https://adoptium.net");
    }
}

// Scan base\*\bin\java.exe and feed every probe into the best tracker.
static void ScanJavaDirs(HWND hwnd, const wchar_t *base, int *bestMajor,
                         wchar_t *bestExe, wchar_t *bestVer) {
    wchar_t probe[MAX_PATH];
    _snwprintf(probe, MAX_PATH - 1, L"%s\\*", base);
    probe[MAX_PATH - 1] = 0;
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(probe, &fd);
    if (h == INVALID_HANDLE_VALUE) return;
    do {
        if (!(fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
        if (fd.cFileName[0] == L'.') continue;
        wchar_t exe[MAX_PATH];
        _snwprintf(exe, MAX_PATH - 1, L"%s\\%s\\bin\\java.exe", base, fd.cFileName);
        exe[MAX_PATH - 1] = 0;
        if (GetFileAttributesW(exe) == INVALID_FILE_ATTRIBUTES) continue;
        wchar_t ver[64];
        if (!ProbeJava(exe, ver, 64)) continue;
        int mj = JavaMajor(ver);
        if (mj > *bestMajor) {
            *bestMajor = mj;
            wcsncpy(bestExe, exe, MAX_PATH - 1); bestExe[MAX_PATH - 1] = 0;
            wcsncpy(bestVer, ver, 63); bestVer[63] = 0;
        }
    } while (FindNextFileW(h, &fd));
    FindClose(h);
}

// ---------------- launch ----------------
static DWORD WINAPI LaunchThread(LPVOID param) {
    LaunchParams *lp = (LaunchParams *)param;
    wchar_t memStr[16];
    _snwprintf(memStr, 15, L"%lu", (unsigned long)lp->memMB);
    memStr[15] = 0;
    delete lp;

    LogLine(L"[launcher] starting: \"%s\" -Xmx%sM -jar \"%s\"",
            g_javaPath, memStr, g_jarPath);

    wchar_t cmd[4096];
    _snwprintf(cmd, 4095, L"\"%s\" -Xmx%sM -jar \"%s\"",
               g_javaPath, memStr, g_jarPath);
    cmd[4095] = 0;
    wchar_t cmdMutable[4096];
    wcscpy(cmdMutable, cmd);

    // working directory = jar folder
    wchar_t cwd[MAX_PATH];
    wcsncpy(cwd, g_jarPath, MAX_PATH - 1);
    cwd[MAX_PATH - 1] = 0;
    wchar_t *slash = wcsrchr(cwd, L'\\');
    if (slash) *slash = 0;

    SECURITY_ATTRIBUTES sa = {sizeof(sa), NULL, TRUE};
    HANDLE rd = NULL, wr = NULL;
    if (!CreatePipe(&rd, &wr, &sa, 0)) {
        LogLine(L"[launcher] pipe creation failed");
        PostMessageW(g_hWnd, WM_APP_DONE, (WPARAM)-1, 0);
        return 1;
    }
    SetHandleInformation(wr, HANDLE_FLAG_INHERIT, HANDLE_FLAG_INHERIT);

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdOutput = wr;
    si.hStdError = wr;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    ZeroMemory(&pi, sizeof(pi));

    if (!CreateProcessW(NULL, cmdMutable, NULL, NULL, TRUE,
                        CREATE_NO_WINDOW, NULL, cwd, &si, &pi)) {
        LogLine(L"[launcher] failed to start java (error %lu)", GetLastError());
        CloseHandle(rd);
        CloseHandle(wr);
        PostMessageW(g_hWnd, WM_APP_DONE, (WPARAM)-1, 0);
        return 1;
    }
    CloseHandle(wr);

    // stream java output into the log pane
    char chunk[4096];
    DWORD read = 0;
    while (ReadFile(rd, chunk, sizeof(chunk), &read, NULL) && read > 0) {
        int wlen = MultiByteToWideChar(CP_UTF8, 0, chunk, (int)read, NULL, 0);
        if (wlen > 0) {
            wchar_t *wbuf = new wchar_t[wlen + 2];
            int conv = MultiByteToWideChar(CP_UTF8, 0, chunk, (int)read, wbuf, wlen);
            if (conv <= 0)
                conv = MultiByteToWideChar(CP_ACP, 0, chunk, (int)read, wbuf, wlen);
            if (conv > 0) {
                wbuf[conv] = 0;
                // route via posted message to keep the edit control thread-safe
                wchar_t *copy = _wcsdup(wbuf);
                PostMessageW(g_hWnd, WM_APP_LOG, 0, (LPARAM)copy);
            }
            delete[] wbuf;
        }
    }
    CloseHandle(rd);
    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD code = 0;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    LogLine(L"[launcher] game exited (code %lu)", code);
    PostMessageW(g_hWnd, WM_APP_DONE, (WPARAM)code, 0);
    return 0;
}

static void LaunchGame(HWND hwnd) {
    if (g_running) return;
    if (!g_javaFound) return;

    DWORD attr = GetFileAttributesW(g_jarPath);
    if (attr == INVALID_FILE_ATTRIBUTES || (attr & FILE_ATTRIBUTE_DIRECTORY)) {
        LogLine(L"[launcher] ERROR: game JAR not found next to the launcher.");
        MessageBoxW(hwnd,
                    L"Put the jarcraft game JAR (jarcraft*.jar) into the\nsame folder as this launcher, then press Play again.",
                    L"jarcraft JAR not found", MB_OK | MB_ICONERROR);
        return;
    }

    DWORD mem = 1024;
    if (IsDlgButtonChecked(hwnd, IDC_RB_2G) == BST_CHECKED) mem = 2048;
    else if (IsDlgButtonChecked(hwnd, IDC_RB_4G) == BST_CHECKED) mem = 4096;

    g_running = TRUE;
    EnableWindow(g_play, FALSE);
    SetWindowTextW(g_status, L"Running...");
    LaunchParams *lp = new LaunchParams();
    lp->memMB = mem;
    CloseHandle(CreateThread(NULL, 0, LaunchThread, lp, 0, NULL));
}

// ---------------- layout ----------------
static BOOL CreateControl(HWND hwnd, const wchar_t *cls, const wchar_t *text,
                          DWORD style, int x, int y, int w, int h, int id) {
    HWND c = CreateWindowExW(0, cls, text, WS_CHILD | WS_VISIBLE | style,
                             x, y, w, h, hwnd, (HMENU)(INT_PTR)id,
                             (HINSTANCE)GetWindowLongPtrW(hwnd, GWLP_HINSTANCE), NULL);
    if (c) SendMessageW(c, WM_SETFONT, (WPARAM)g_font, TRUE);
    return c != NULL;
}

static void BuildUI(HWND hwnd, HINSTANCE hInst) {
    CreateControl(hwnd, L"STATIC", L"legacy jarcraft launcher",
                  SS_LEFT | WS_GROUP, 14, 10, 300, 20, IDC_TITLE);
    SendMessageW(GetDlgItem(hwnd, IDC_TITLE), WM_SETFONT, (WPARAM)g_fontBold, TRUE);

    // Java group
    CreateControl(hwnd, L"BUTTON", L"Java Runtime",
                  BS_GROUPBOX, 10, 36, 420, 78, IDC_GRP_JAVA);
    CreateControl(hwnd, L"STATIC", L"Not scanned yet.",
                  SS_LEFT, 22, 54, 330, 40, IDC_JAVA_INFO);
    CreateControl(hwnd, L"BUTTON", L"&Refresh",
                  BS_PUSHBUTTON, 340, 74, 78, 26, IDC_BTN_REFRESH);

    // Memory group
    CreateControl(hwnd, L"BUTTON", L"Memory Allocation",
                  BS_GROUPBOX, 10, 120, 420, 56, IDC_GRP_MEM);
    CreateControl(hwnd, L"BUTTON", L"1 GB",
                  BS_RADIOBUTTON | WS_GROUP, 30, 140, 80, 20, IDC_RB_1G);
    CreateControl(hwnd, L"BUTTON", L"2 GB",
                  BS_RADIOBUTTON, 150, 140, 80, 20, IDC_RB_2G);
    CreateControl(hwnd, L"BUTTON", L"4 GB",
                  BS_RADIOBUTTON, 270, 140, 80, 20, IDC_RB_4G);
    CheckRadioButton(hwnd, IDC_RB_1G, IDC_RB_4G, IDC_RB_2G);

    // Buttons
    CreateControl(hwnd, L"BUTTON", L"&Play",
                  BS_DEFPUSHBUTTON, 10, 184, 205, 34, IDC_BTN_PLAY);
    CreateControl(hwnd, L"BUTTON", L"E&xit",
                  BS_PUSHBUTTON, 225, 184, 205, 34, IDC_BTN_QUIT);

    // Log group
    CreateControl(hwnd, L"BUTTON", L"Log",
                  BS_GROUPBOX, 10, 226, 420, 168, IDC_GRP_LOG);
    g_log = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", NULL,
                            WS_CHILD | WS_VISIBLE | WS_VSCROLL | WS_HSCROLL |
                            ES_MULTILINE | ES_AUTOVSCROLL | ES_AUTOHSCROLL | ES_READONLY,
                            22, 244, 396, 138, hwnd, (HMENU)(INT_PTR)IDC_LOG,
                            hInst, NULL);
    SendMessageW(g_log, WM_SETFONT, (WPARAM)g_font, TRUE);

    g_status = CreateWindowExW(0, L"STATIC", L"Starting...",
                               SS_LEFT, 14, 398, 410, 18, hwnd,
                               (HMENU)(INT_PTR)IDC_STATUS, hInst, NULL);
    SendMessageW(g_status, WM_SETFONT, (WPARAM)g_font, TRUE);
}

// ---------------- window proc ----------------
static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: {
        g_hWnd = hwnd;
        g_font = (HFONT)GetStockObject(DEFAULT_GUI_FONT);
        LOGFONTW lf;
        GetObjectW(g_font, sizeof(lf), &lf);
        lf.lfWeight = FW_BOLD;
        g_fontBold = CreateFontIndirectW(&lf);
        g_play = GetDlgItem(hwnd, IDC_BTN_PLAY);
        BuildUI(hwnd, (HINSTANCE)GetWindowLongPtrW(hwnd, GWLP_HINSTANCE));

        // jar path = launcher dir + detected jar name
        wchar_t exe[MAX_PATH];
        GetModuleFileNameW(NULL, exe, MAX_PATH);
        wchar_t *slash = wcsrchr(exe, L'\\');
        if (slash) *(slash + 1) = 0;
        if (FindJar(exe, g_jarPath, MAX_PATH)) {
            LogLine(L"[launcher] game file: %s", g_jarPath);
        } else {
            g_jarPath[0] = 0;
            LogLine(L"[launcher] WARNING: no jarcraft*.jar found next to the launcher.");
            LogLine(L"[launcher] put the game JAR in the same folder, then press Refresh.");
        }

        LogLine(L"legacy jarcraft launcher - ready");
        DetectJava(hwnd);
        return 0;
    }
    case WM_COMMAND:
        switch (LOWORD(wp)) {
        case IDC_BTN_PLAY:
            LaunchGame(hwnd);
            break;
        case IDC_BTN_QUIT:
            DestroyWindow(hwnd);
            break;
        case IDC_BTN_REFRESH:
            // re-scan for the game jar too, in case the user just added it
            {
                wchar_t exe[MAX_PATH];
                GetModuleFileNameW(NULL, exe, MAX_PATH);
                wchar_t *sl = wcsrchr(exe, L'\\');
                if (sl) *(sl + 1) = 0;
                if (FindJar(exe, g_jarPath, MAX_PATH)) {
                    LogLine(L"[launcher] game file: %s", g_jarPath);
                } else {
                    g_jarPath[0] = 0;
                    LogLine(L"[launcher] still no jarcraft*.jar next to the launcher.");
                }
            }
            DetectJava(hwnd);
            break;
        case IDC_RB_1G: case IDC_RB_2G: case IDC_RB_4G:
            CheckRadioButton(hwnd, IDC_RB_1G, IDC_RB_4G, LOWORD(wp));
            break;
        }
        break;
    case WM_APP_LOG: {
        wchar_t *text = (wchar_t *)lp;
        if (text) {
            AppendLogW(g_log, text);
            free(text);
        }
        return 0;
    }
    case WM_APP_DONE:
        g_running = FALSE;
        EnableWindow(g_play, g_javaFound ? TRUE : FALSE);
        SetWindowTextW(g_status, g_javaFound ? L"Ready." : L"Java not found.");
        return 0;
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

// ---------------- entry ----------------
int wWinMain(HINSTANCE hInst, HINSTANCE, LPWSTR, int nCmdShow) {
    WNDCLASSW wc;
    ZeroMemory(&wc, sizeof(wc));
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInst;
    wc.hCursor = LoadCursorW(NULL, (LPCWSTR)IDC_ARROW);
    wc.hIcon = LoadIconW(hInst, MAKEINTRESOURCEW(1));
    wc.hbrBackground = CreateSolidBrush(RGB(212, 208, 200)); // Win98 gray
    wc.lpszClassName = L"JarcraftLauncherWnd";
    RegisterClassW(&wc);

    // client area 444 x 424
    RECT rc = {0, 0, 444, 424};
    AdjustWindowRect(&rc, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
                     FALSE);

    HWND hwnd = CreateWindowW(wc.lpszClassName, L"legacy jarcraft launcher",
                              WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
                              CW_USEDEFAULT, CW_USEDEFAULT,
                              rc.right - rc.left, rc.bottom - rc.top,
                              NULL, NULL, hInst, NULL);
    if (!hwnd) return 1;

    ShowWindow(hwnd, nCmdShow);
    UpdateWindow(hwnd);

    MSG msg;
    while (GetMessageW(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    return (int)msg.wParam;
}
