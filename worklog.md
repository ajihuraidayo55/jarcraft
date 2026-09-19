# jarcraft worklog (recreated after workspace reset on 2026-09-19 09:14)

NOTE: The original workspace (game source, launcher, toolchain, all
deliverables) was wiped by a platform workspace reset between turns. This
was the root cause of the user's download failures: files registered in the
UI vanished from disk, so the download gateway could not serve them.
Everything below was rebuilt from scratch in Task 7. Deliverables and all
sources are now committed to git (commit 7e2e84b) to survive future resets.

---
Task ID: 7
Agent: Super Z (main)
Task: Rebuild entire jarcraft deliverable set after workspace reset; fix renderer; redeliver as zip

Work Log:
- Diagnosed workspace reset (download/ nearly empty, git had only initial commit, no artifacts anywhere on disk)
- Rebuilt user-level toolchain: apt-get download (41 pkgs, 221MB) + dpkg -x -> toolchain/ (OpenJDK 21 jdk/jre-headless + jre, mingw-w64 gcc/g++/binutils/windres); verified javac 21 + x86_64-w64-mingw32-g++ 14-posix + link test
- Rewrote 12 Java classes (Noise, Blocks, Textures, World, WorldGen, Player, Input, Renderer, Game, HUD, Main, SelfTest), Java 8 bytecode (major 52)
- Renderer iterations: fan-of-sub-rays with span filling -> fixed exact-boundary floor() bug (epsilon nudge) -> fixed degenerate cornerOf (diagonal-only corners) -> z-buffer -> final hybrid: heightmap strip ground pass (Comanche-style, gap-free) + 10-ray fan for sides/trees/cliffs
- Performance: 418ms -> 24ms -> 48ms first-shot; steady-state 52-53 fps under Xvfb at 854x480
- Fixed spawn (findSpawn on grass), start time = morning (DAY_TICKS/3), water rendering via surfaceY<=SEA
- Xvfb GUI smoke test with Robot screenshot: terrain/trees/water/sky verified visually; fps log added
- Rewrote launcher.cpp (~500 lines): Win98-style UI, ProbeJava/JavaMajor/DetectJava (PATH, JAVA_HOME, Program Files scan), FindJar flexible jarcraft*.jar lookup + Refresh rescan, pipe-logged launches
- Generated multi-size ICO via PIL; windres VERSIONINFO; cross-compiled 649KB static exe (imports: GDI32/KERNEL32/USER32/msvcrt only)
- Packaged download/: jarcraft-alpha1.0.0.jar (30KB), legacy-jarcraft-launcher.exe (649KB), jarcraft.zip (348KB, contains jarcraft/ folder + readme EN/JP); zip integrity OK
- Final checks: SELFTEST-PASS (0 failures), class version 52, PE32+ valid
- Committed ALL sources + deliverables to git (7e2e84b) with .gitignore for toolchain/debs

Stage Summary:
- Root cause of download failures = platform workspace reset wiping files between turns
- download/ = 3 verified files; primary recommendation: jarcraft.zip
- Recovery procedure if reset happens again: git checkout / rebuild via scripts/ (env.sh, build_jar.sh, build_launcher.sh, make_icon.py, make_bundle.py, gui_smoke.sh)

---
Task ID: 8
Agent: Super Z (main)
Task: Push jarcraft repository to GitHub (ajihuraidayo55/jarcraft)

Work Log:
- User asked to push to https://github.com/ajihuraidayo55/jarcraft (public, EPL-2.0, initial commit with LICENSE only)
- Two fine-grained PATs failed with 403 (Contents: read/write never granted; API write probe returned 404 confirming missing Contents permission)
- Switched to GitHub OAuth device flow (client_id 178c6fc778ccc68e1d6a, scope=repo); first user code expired, second code DFE2-88ED authorized successfully
- Merged remote initial commit (LICENSE, e812458) with --allow-unrelated-histories; dropped platform auto-checkpoint junk commit (scripts/_remote_check gitlink) via reset+re-merge
- Pushed main to GitHub and verified via ls-remote
- User advised to revoke both fine-grained PATs and the GitHub CLI OAuth grant after push

Stage Summary:
- GitHub repo now mirrors full local history: game source, launcher, scripts, deliverables, LICENSE (EPL-2.0)
- GitHub serves as reset-resistant backup channel for deliverables
