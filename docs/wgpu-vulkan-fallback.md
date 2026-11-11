# 一次 Intel 核显 Vulkan 驱动崩溃导致的 Windows 启动失败

2026-11-11 · ClipSyncRust 桌面端 · eframe 0.37 / wgpu 30 / winit

## 现象

Windows 11（build 26200）上安装最新版 ClipSyncRust 后，双击图标**没有任何反应**：没有窗口、没有错误弹窗、托盘图标也不出现。进程在事件查看器里留下一条记录：

| 项 | 值 |
|---|---|
| 故障模块 | `igvk64.dll`（Intel Vulkan 驱动） |
| 驱动版本 | `31.0.101.2140` |
| 异常码 | `0xc0000005`（访问越界） |
| 异常偏移 | `0x64ea82` |
| GPU | Intel UHD Graphics，Ice Lake GT1 |

`0xc0000005` 是 Windows 的 `STATUS_ACCESS_VIOLATION`，发生在驱动 DLL 内部，不是应用代码抛出的异常。

## 排查

应用是 `eframe` → `egui_wgpu` → `wgpu` 这条原生 Rust GUI 链路，Windows 上用 `x86_64-pc-windows-msvc` 编译。几个方向逐一排除：

- **RTSS（RivaTuner Statistics Server）的 Vulkan 注入层**：它在系统里注册了隐式 layer，很容易被怀疑成元凶。排除后崩溃依旧。
- **VC++ 运行库缺失**：`windows-msvc` 默认动态链接 CRT，缺 `vcruntime140.dll` 时进程会在进 `main` 之前被加载器拒绝。v0.0.6 顺手把 CRT DLL 做成 app-local 部署，对真正缺运行库的机器有用，但**不是这次的根因**——出问题的机器能一路跑到 `igvk64.dll`。
- **应用自身的启动逻辑**：`main.rs` 里装有 panic hook，启动失败会写 `startup.log` 并弹 `MessageBoxW`。日志和弹窗都没有出现，说明进程根本没走到 Rust 的错误处理路径。

关键线索是崩溃模块名 `igvk64.dll`——Intel 的 **Vulkan** ICD。

## 根因

wgpu 的默认后端集合是 `Backends::PRIMARY | Backends::GL`，而 `PRIMARY` 包含 `VULKAN`、`DX12`、`METAL`、`BROWSER_WEBGPU`：

```rust
const PRIMARY = Self::VULKAN.bits()
    | Self::METAL.bits()
    | Self::DX12.bits()
    | Self::BROWSER_WEBGPU.bits();
```

`egui_wgpu::WgpuSetupCreateNew::without_display_handle()` 直接用 `Backends::from_env().unwrap_or(PRIMARY | GL)` 建 instance。Windows 上 Vulkan 排在适配器的优先位置，于是 wgpu 加载 `igvk64.dll` 去枚举物理设备。Ice Lake GT1 搭配 `31.0.101.2140` 这版驱动在初始化路径上越界，整个过程以 native 异常结束。

这也是**临时绕过办法 `WGPU_BACKEND=dx12` 有效**的原因：`Backends::from_env()` 会把后端集合收敛成只有 `DX12`，Vulkan 分支完全不初始化，`igvk64.dll` 根本不会加载。

## 为什么不能"捕获异常再回退"

很容易想到：把 wgpu 初始化包一层，失败了就切 DX12。这条路在这里走不通。

`catch_unwind` 只能捕获 Rust 的 `panic!`；`run_native` 返回的 `Result` 只能表达 wgpu 自己发现的错误。而 `0xc0000005` 是驱动内部的非法内存访问，进程被系统直接终止，既没有 panic 也没有 `Err`，栈已经不在应用代码里。`startup::fail` 只在 `run_native` **正常返回** `Err` 时才执行，所以连错误弹窗都来不及出现。

Windows 上可以用 SEH（`AddVectoredExceptionHandler`）拦下这个异常，但此时进程状态已被驱动破坏，继续在同一进程里重建 wgpu 实例并不可靠。要让"驱动不带走进程"，可行的边界是**进程**：让一个没有图形栈的父进程负责启动渲染子进程，子进程崩了父进程还活着，读退出码、换后端、重启。

## 修复：进程级看门狗 + 自动降级 DX12

新增 `crates/clipsync-desktop/src/gpu.rs`，`main()` 的第一行交给 `gpu::guard()`：

```
父进程 guard()
  ├─ 读 app_dir()/gpu-backend（上次是否崩过）
  ├─ spawn 自身，带 CLIPSYNC_RENDER_CHILD=1
  └─ wait() → 看退出码
       ├─ 正常退出 / 普通业务错误 → 直接退出，不干扰
       └─ native 异常 → 记住 dx12，注入 WGPU_BACKEND=dx12 重启一次
```

几个设计点：

- **崩溃判定**：Windows 的 native 异常退出码是 `0xC0000000` 以上的 NTSTATUS（如 `0xC0000005`、`0xC0000409`）。另外 `run_native` 返回 `Err` 这类**能捕获**的初始化失败，子进程以约定的退出码 `88` 退出，父进程同样按"渲染失败"处理。用户主动关窗是退出码 0，`main` 返回 `Err` 是 1，panic 是 101，这些都不会触发重启，避免误伤。
- **只重启一次**：记住降级结果写进 `gpu-backend` 文件，之后每次启动**直接**以 DX12 拉起，不必每次先崩一次再恢复。若 DX12 仍然崩溃，就不再循环，弹中英双语的错误框提示升级驱动。删掉 `gpu-backend` 文件即可恢复默认后端。
- **抑制 WER 弹窗**：`main()` 一开始就调用 `SetErrorMode(SEM_NOGPFAULTERRORBOX)`，父进程设置的 error mode 会被 `CreateProcess` 派生出的子进程继承。否则 Windows 的"程序已停止工作"对话框会挡在父进程 `wait()` 前面，自动恢复就无从谈起。
- **尊重显式配置**：如果用户自己设了 `WGPU_BACKEND`，看门狗完全不接管——这是留给高级用户和调驱动的开关，也是前面那个临时绕过办法的入口。
- **只在 Windows 生效**：macOS 走 Metal、Linux 不受影响，`guard()` 在非 Windows 平台直接返回。

UI 侧顺带把启动错误弹窗改成中英双语，并在文案里去掉了已经不准确的 "OpenGL 不可用"（现在渲染后端是 wgpu）。

## 验证

- `gpu.rs` 里对崩溃码判定做了单测：`0xC0000005` / `0xC0000409` / 约定的 `88` / 信号终止会触发回退；退出码 0 / 1 / 101 不会。
- 本地 `cargo test -p clipsync-desktop` 全量通过；Windows 专属代码由 CI 的 `x86_64-pc-windows-msvc` 构建验证。
- 目标机器验证路径：全新安装后首次启动若触发 Vulkan 崩溃，父进程自动以 DX12 重启；`%APPDATA%\ClipSync\gpu-backend` 生成后，后续启动不再接触 Vulkan。

## 几点经验

1. **"进程没起来"和"进程崩了"是两类问题**。前者查加载器依赖，后者查 native 崩溃模块。GUI 子系统没有 stderr，很容易把后者误判成前者，所以先把能落盘的日志和崩溃转储拿到手。
2. **native 崩溃不能靠应用内错误处理兜底**。凡是跨进第三方驱动的初始化，要恢复就得把决策放到进程外。
3. **后端白名单比事后回退更稳**。`WGPU_BACKEND` 这类环境变量在 wgpu 里优先级最高，既可以做用户侧的临时绕过，也可以由程序在重启时注入，两种用法不冲突。
4. **自动恢复要有终止条件**。记住降级状态 + 最多重启一次，否则驱动问题会变成无限重启循环，比打不开更糟。
