# 记牌器 Android APP - 编译安装指南

## 📱 项目简介

这是一个独立的Android APP，安装到手机后可以后台运行，悬浮窗记牌器可以：
- ✅ 悬浮在屏幕最顶层（覆盖所有应用）
- ✅ 自由拖拽位置
- ✅ 点击/长按快速记录
- ✅ 双击展开/收起
- ✅ 自动保存位置

## 🛠️ 编译方法

### 方法一：使用 Android Studio（推荐）

1. **下载 Android Studio**
   - 访问 https://developer.android.com/studio 下载安装

2. **打开项目**
   ```
   打开 Android Studio
   点击 "Open" 或 "File → Open"
   选择文件夹: D:\Software\斗地主记牌器\AndroidAPP
   ```

3. **同步项目**
   - 等待 Gradle 同步完成
   - 首次可能需要下载依赖，耐心等待

4. **编译APK**
   - 点击菜单：Build → Build Bundle(s) / APK(s) → Build APK(s)
   - 等待编译完成

5. **获取APK**
   - 编译成功后点击通知中的 "locate"
   - APK文件位置：`app/release/app-release.apk`

### 方法二：使用命令行编译

```bash
cd D:\Software\斗地主记牌器\AndroidAPP

# Windows
gradlew.bat assembleRelease

# 生成的APK位置: app\build\outputs\apk\release\app-release.apk
```

## 📲 安装方法

### 方式一：直接安装APK

1. 将生成的APK文件传输到手机
2. 在手机上点击APK文件安装
3. 允许安装未知来源应用

### 方式二：USB调试安装

```bash
# 手机开启USB调试后，连接电脑
adb install app-release.apk
```

## 🔐 权限说明

安装后首次使用需要授予以下权限：

| 权限 | 用途 |
|------|------|
| 悬浮窗权限 | 在屏幕上显示记牌器 |
| 通知权限 | 后台运行保活 |

## 🎯 使用方法

1. **启动APP**
   - 打开"记牌器"应用

2. **开启悬浮窗**
   - 点击"启动"按钮
   - 授予悬浮窗权限（首次使用）

3. **使用悬浮窗**
   - 拖拽标题栏移动位置
   - 点击牌面：数量 -1
   - 长按牌面：数量 +1
   - 双击悬浮窗：展开/收起
   - 点击重置按钮：恢复初始状态

4. **后台运行**
   - 按Home键返回桌面
   - 悬浮窗会继续显示
   - 可以打开斗地主游戏

## 📁 项目结构

```
AndroidAPP/
├── app/
│   ├── src/main/
│   │   ├── java/com/cardcounter/
│   │   │   ├── MainActivity.java          # 主Activity
│   │   │   ├── FloatWindowService.java    # 悬浮窗服务
│   │   │   ├── CardDataManager.java       # 数据管理
│   │   │   └── BootReceiver.java          # 开机启动
│   │   ├── res/
│   │   │   ├── layout/                    # 布局文件
│   │   │   ├── drawable/                  # 图片资源
│   │   │   └── values/                    # 资源值
│   │   └── AndroidManifest.xml            # 清单文件
│   └── build.gradle                       # 模块构建
├── build.gradle                           # 项目构建
├── settings.gradle                        # 项目设置
└── gradle.properties                      # Gradle配置
```

## ⚙️ 技术要点

- **悬浮窗类型**: `TYPE_APPLICATION_OVERLAY`
- **拖拽实现**: `onTouch` 监听 + `updateViewLayout`
- **位置保存**: `SharedPreferences`
- **前台服务**: 保持后台运行
- **数据单例**: `CardDataManager` 单例模式

## 🔧 常见问题

**Q: 编译报错？**
A: 检查 JDK 版本（需要 JDK 8+），确保 Android SDK 已安装

**Q: 悬浮窗不显示？**
A: 检查是否授予了悬浮窗权限：设置 → 应用管理 → 记牌器 → 权限

**Q: 如何修改APP图标？**
A: 替换 `app/src/main/res/mipmap-*/ic_launcher.png`

**Q: 如何修改APP名称？**
A: 修改 `app/src/main/res/values/strings.xml` 中的 `app_name`

## 📝 版本信息

- 版本号: 1.0
- 最小SDK: 24 (Android 7.0)
- 目标SDK: 34 (Android 14)

---

**祝游戏愉快！🎉**
