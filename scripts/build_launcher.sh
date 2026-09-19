#!/bin/bash
# Cross-compile "legacy-jarcraft-launcher.exe" (static, no MinGW runtime DLLs)
set -e
source /home/z/my-project/scripts/env.sh
TC=/home/z/my-project/toolchain
cd /home/z/my-project/launcher

RC=$TC/usr/bin/x86_64-w64-mingw32-windres
CXX=$TC/usr/bin/x86_64-w64-mingw32-g++-posix

"$RC" --include-dir "$TC/usr/x86_64-w64-mingw32/include" --include-dir "$TC/usr/share/mingw-w64/include" \
    -O coff resource.rc -o resource.o
"$CXX" -O2 -municode -mwindows -static -static-libgcc -static-libstdc++ \
    launcher.cpp resource.o \
    -o "legacy-jarcraft-launcher.exe" \
    -luser32 -lkernel32 -lgdi32

cp -f "legacy-jarcraft-launcher.exe" "/home/z/my-project/download/legacy-jarcraft-launcher.exe"
ls -la "legacy-jarcraft-launcher.exe"

# Dependency check: list DLL imports
"$TC/usr/bin/x86_64-w64-mingw32-objdump" -p "legacy-jarcraft-launcher.exe" | grep "DLL Name" | sort -u
