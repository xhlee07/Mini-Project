# CTFLY Android：接下来这样做

项目位置：`C:\Users\User\Mini Porject\CTFLY-Android`

这是 Kotlin + Jetpack Compose 原生 Android 项目。应用源码和测试都是 `.kt`，没有手写 Java 页面或 Java 业务文件。Android 的构建工具和部分第三方库本身会用 JVM/Java，这是 Android Studio 正常的底层工具链。

## 最新 1.13 / Staff 1.4（2026-10-11）

顾客版 **CTFLY-Gaming-Cafe-1.13.apk / code 14**，员工版 **Staff-CTFLY-1.4.apk / code 5**。一起覆盖安装即可。最短 30 分钟、当前开始时间、前方错误提示、登录保留、比赛与优惠码管理、完整 PDF 收据已加入。

更新内容见 `UPDATE_1.13_中文.md`。数据库与自动寄信接口已应用到你自己的 Supabase；**目前只剩发信邮箱配置**，见 `MAIL_SETUP_中文.md`。App 收据/PDF可用，配置前不会寄邮件。付款仍为测试，不会扣钱。Staff 的交易记录现在在顶部三点菜单里。

下面是历史更新记录；已完成的云端设置不要重复执行。

## 1.12 测试付款（2026-10-11）

最新顾客版：**CTFLY-Gaming-Cafe-1.12.apk / code 13**；最新员工版：**Staff-CTFLY-1.3.apk / code 4**。一起覆盖安装即可，保持原来的登录账号。

预约、点餐与活动报名会进入测试结账。TNG、FPX 和银行卡均为 **服务端模拟付款，不会扣钱**，不用填写真实账号或付款凭据。失败、处理中、取消、重试、测试退款和 TEST 收据都可演示。

顾客在「我的预约 → 付款记录」查看交易；员工在底部「交易记录」查看顾客测试付款。测试积分与正式积分分开，不计入正式营业收入。

付款迁移已经应用到现有学校项目，无需再手动运行 SQL。迁移文件编号与云端迁移记录一致。演示方法见 `PAYMENTS_中文.md`；验证与已存在的数据库提醒见 `PAYMENT_VERIFICATION.md`。

## 1.11 各层卫浴与赛道建筑调整（2026-10-11）

最新顾客版：**CTFLY-Gaming-Cafe-1.11.apk / code 12**，可覆盖安装 1.10，无需卸载或新增 SQL。

- 你圈出的旧灰色建筑原本是没有内部结构的赛道配套外壳。现在已替换成停车区步行侧的卫浴楼，避开赛道、护栏和赛车镜头范围。
- 电竞层、桌球层、休闲层和赛道区各有洗手区、三个独立厕所、两个独立淋浴间。淋浴间有花洒、控制器、置物架和地漏；厕所和淋浴间都有实体隔墙。
- 每个入口与隔间的门都能开关，并有转动动画、碰撞和防夹检查。站在门的转动范围时，按提示退开后再操作。楼层切换和屏幕重建会保留门状态。
- 在每层顶部目的地菜单选择 **Washroom / 洗手间**；靠近门会出现对应厕所、淋浴间或入口的操作按钮。地图也标出卫浴布局。
- 材质增加表面细节，木门纹理随门移动，调整室内亮度、草地颜色和树冠轮廓。当前仍是实时 3D 概念场景，未达到照片或原神成品画质。

检查记录见 `VERIFICATION.md`。使用专用模拟器；仍需在你的 Huawei nova 5T / EMUI 12 上确认实际效果。

## 1.10 手机操作区重新排版（2026-10-09）

顾客版 **1.10 / code 11**：`CTFLY-Gaming-Cafe-1.10.apk`，直接覆盖安装 1.9，无需卸载或新增 SQL。

- Jump 改为大号黑底、白色圆边、向上箭头，和 Run 一起放在右下角。
- Take a kart、Ground exit / Change floor、厕所门动作放到上方；左下摇杆只管移动，右下只留移动或驾驶按钮。
- 手机赛车的 Restart / Exit kart 放在顶部同一行；速度和圈数框缩短，地图与踏板分开，中间保留车辆视野。
- 手机按可用高度分配控件尺寸，油门与刹车仍有至少 80dp 高的触控区；倒车使用向下箭头，与油门/刹车区分。
- 手机目的地使用滚动弹窗，避免列表挤压场景；平板保留横向目的地选择。画质、楼层和帮助内容仍可滚动。
- 踏板独立追踪手指，左手按住摇杆时，右手可以反复松开、再按踏板。松手或切到后台会释放输入。
- 赛车地图可以真正隐藏或显示；低高度布局保留当前圈速计时。
- 停车区铺装面略微抬高，减少与草地贴得太近造成的遮盖和闪烁。

语言、背景、意见反馈、注册登录限制及自动横屏均保留。此次检查使用模拟器及隔离测试数据，不代表已在 Huawei 真机、云端支付或真实邮件上逐项验证。

## 1.9 手机横屏与操作修复（2026-10-09）

顾客版 **1.9 / code 10**：`CTFLY-Gaming-Cafe-1.9.apk`，在手机上直接安装更新。

- 进入 3D Tour 自动切换为横屏，退出后恢复进入前的方向。手机横屏默认扩大场景；上方展开图标仍可显示目的地，右下角 Change floor 可换楼层。
- 3D 改为普通界面中的 TextureView 渲染，处理手机旋转、裁切及界面叠加，保留 Ultra / Balanced / Smooth 画质选项。画质与换楼层弹窗支持滚动，横屏也能访问底部选项。
- 左手摇杆与右手转头分开追踪，摇杆按住时可以反复抬起右手、继续滑动转头；水平转头可连续绕圈，并加入轻微平滑。松开摇杆或切到后台会停止移动。
- 日期栏减少内部空隙，Day 不再拆成两行；窄屏或大字体时改为 Day / Month 一排、Year 下一排。
- 已取消 **Explore the club first**；必须注册并登录才能进入场景和主功能。旧版保存的预览模式也不能绕过登录。登录前仍可设置语言和背景。

本轮无需新增 SQL，员工版仍可使用 1.2。以前的反馈功能仍需完成下面的 006 云端设置。模拟器检查不等于 Huawei nova 5T / EMUI 12 真机验证；新 APK 需要在该手机上确认实际表现。

## 1.8 设置与意见反馈（2026-10-09）

顾客版 **1.8 / code 9**：`CTFLY-Gaming-Cafe-1.8.apk`。员工版 **1.2 / code 3**：`Staff-CTFLY-1.2.apk`。两版都需要更新，员工版才能看到新的反馈收件页。

- 点右上角 **Settings / 设置** 齿轮。登录前也可以调整语言和颜色。
- 语言提供 **English、简体中文、Bahasa Melayu**；切换后立即更新导航、登录、预约、会员、设置及主要员工操作文字。用户输入的反馈保留原文，3D 模型中的图片和烘焙标牌仍保留原样。
- 背景提供 **深灰、深蓝、深绿、紫色**；页面和卡片一起切换，维持深色界面的文字对比。语言和颜色存在本机，旋转、重开和退出账号后都保留；顾客版与员工版各自设置。
- 顾客登录后在设置提交反馈：分类、1–5 分评分、标题（3–120 字）及内容（10–2000 字）。发送成功才清空输入；提交失败保留草稿，重试沿用同一个请求编号，避免重复记录。下方 **My feedback / 我的反馈** 可以查看处理状态。
- 员工或管理员点上方 **Customer feedback / 顾客反馈** 图标，或 **Settings → Open feedback inbox**；可以筛选新提交、已查看、已处理，并更新状态。反馈显示提交者姓名、时间、分类、评分和原文，不公开给其他顾客。
- **在原 Supabase 的 SQL Editor 执行 `supabase/006_settings_feedback.sql` 一次**，然后在两个 App 点刷新。006 可重复运行，不删除已有反馈、账号或预约。语言和背景离线可用，提交与同步反馈需要网络和这次云端升级。若上轮 005 尚未执行，先执行 005，再执行 006；不要重跑 001 或 004。

006 已在本地隔离 PostgreSQL 测试，尚未应用到你的云端项目。不能把本地模拟器的示例反馈当成真实云端已提交。

## 1.7 大踏板、私人房与赛车模拟器（2026-10-09，历史更新）

顾客 APK 版本 **1.7 / code 8**，路径 `app/build/outputs/apk/debug/app-debug.apk`。这轮包含新的云端设施，先在已有 Supabase 项目的 SQL Editor 执行 **`supabase/005_private_rooms_simulators.sql`**，然后刷新 Play；不要重跑 001 或 004。005 在本地隔离数据库测试过，尚未写入你的云端项目。

- Go-kart 改用大号并排踏板 **BRAKE / GAS**，并加入 **REVERSE**。横屏三个键并排；窄竖屏将倒车放在踏板上方，保留左侧大摇杆空间。单独刹车减速，车速够快时 **刹车 + 左右转向 = 漂移**，不再有独立 DRIFT 键。路肩只温和减速，靠墙仍能转向或倒车恢复。
- **Restart / Exit kart** 移到上方工具栏。Restart 要再确认一次，避免误触重开；本机最佳圈速保留。Jump 增大为 80dp 高的大按钮。
- 电竞区重排为四排、每排六个座位，共 24 台普通 PC；蓝白灯带、端板、天花灯板和座椅轮廓按参考网吧风格重新建模。
- **Play → VIP room → 选择 1–4 人**，自动筛选能容纳人数的房间。9 间独立房：3 间单人、3 间双人、3 间四人，每个玩家有独立 PC；3 人使用四人房。预约锁定整间房，不能再让另一组订同一间同一时段。
- 暂定整间房每小时：**单人 RM15、双人 RM28、四人 RM52**。三人订四人房仍为 RM52；界面会显示整间收费及主机数量。历史预约保留原本金额、ID、二维码和付款；005 将已知旧 VIP rig 直接转成房间资源，保留状态。
- **Play → Racing simulator → Standard / VIP**：普通 4 台（RM20/小时），VIP 4 台（RM35/小时），每台 1 人。3D 模型补齐铝型材槽纹、固定座椅、安全带孔、宽屏、方向盘、三踏板和换挡杆；VIP 在私人模拟器隔间中。
- 价格可在员工版用管理员账号进入设施编辑，修改 **Hourly rate**（单位 sen：RM15 填 1500）。重复运行 005 不会覆盖已调整的房价或复制设施。

这些模型和实时灯光比 1.6 更细致，仍是实时 3D 概念场景，尚不是实拍照片或《原神》成品画面的同等质量。查看 `test-artifacts/CTFLY-test/usability-*.png` 的实际截图。

前面截图的 `Unable to resolve host ... No address associated with hostname` 表示设备当时无法解析 Supabase 域名，登录请求还没到账号验证。2026-10-09 在这台电脑检查相同域名得到正常 DNS 地址；这不能证明平板当时的网络正常。若平板仍报相同错误，先换手机热点再试，并检查该设备的 Private DNS/VPN 设置；不用为这个错误重新注册账号或重跑建表 SQL。

### 1.6 操作说明（旧版本）

这次升级无需新增 SQL。顾客版使用 `app/build/outputs/apk/debug/app-debug.apk`（1.6 / code 7），或 Android Studio 选 `app` 后 Run。

- 电竞区是连续排桌：24 台普通 PC 与 8 台 VIP，统一黑色桌面、曲面屏、显示器支架、键鼠、灯条、耳机和圆滑椅背。
- 默认步行提速到 4.5 m/s；点 **Run** 切换 7.2 m/s，再点 **Running** 恢复步行。点 **Jump** 跳跃并自然落地，不能连点飞起来。
- **G Social lounge → Washroom**：先进入洗手区，靠近三个隔间的门会出现 **Open door / Close door**。关门阻挡通行；站在门口时不能把门关到身体里，退开即可关闭。
- **G Kart circuit → Go-kart**：现在是地面赛车场，有约 582 米跑道、长直道、连续弯道、维修区和看台。靠近维修区点 **Take a kart**，倒数后可以驾驶。
- 左摇杆左右转向；按住 **GAS** 加速，按住 **BRAKE** 减速。车速够快时按住 **DRIFT** 并转向可以漂移，漂移键会维持油门，支持两只拇指操作；没有道具、技能或氮气。
- 跟着地图的下一检查点，按顺序通过 8 个门架才能完成一圈。显示速度、圈数、当前圈时间及本机最佳成绩；**Restart** 重新开始并保留最佳成绩，**Exit kart** 返回维修区。最佳圈速保存在本机。
- 跳跃、跑车和场景都可离线使用；预约、登录和付款仍按原有云端流程。旋转屏幕保留位置、赛车进度和门状态，离开或暂停释放按住的控制键。

这是改进后的实时 3D 概念场景，仍不是实拍照片或照片扫描模型。实际效果看 `test-artifacts/CTFLY-test/experience-*.png`；不是宣传图替代实际画面。

## 两个独立 App（2026-10-05）

- `app` module：**CTFLY Gaming Cafe**，顾客预约、商品、赛事和会员；没有 Staff 登录入口。
- `staff` module：**Staff CTFLY**，员工运营、收款、扫码、排班、交接、设备及管理员管理；没有顾客购物/预约/公开注册入口。
- Android Studio 工具栏选择 `app` 或 `staff`，再按 Run。两个 APK 可以同时安装；顾客版更新原 `com.ctfly.app`，员工版使用新的 `com.ctfly.staff`。
- 两个 App 共用已有 Supabase，不需要新建数据库、重跑 001/003 或重新注册已有账号。原来的 staff/admin 邮箱与密码在员工版登录，customer 在顾客版登录。
- 各自保存登录状态。顾客账号不能进入员工版，staff/admin 账号不能进入顾客版。旧版仍登录员工账号的顾客 App 更新后会返回登录页，改用员工版即可。
- APK 路径：`app/build/outputs/apk/debug/app-debug.apk` 与 `staff/build/outputs/apk/debug/staff-debug.apk`。

## 1. 建立 Supabase 数据库（需要你做）

> 已有项目不要重跑 001。本次 1.4 新增预约设施，需要单独运行 `supabase/004_venue_campus.sql`。

### 已有项目升级到 1.4（2026-10-08）

1. Supabase → **SQL Editor → New query**。
2. 打开 `supabase/004_venue_campus.sql`，复制全部内容并点击 **Run**。
3. 看到结果后，重新打开顾客 App 或刷新 Play。目标设施：32 台 PC（24 Standard + 8 VIP）、8 Pool、4 Snooker、4 Console、4 Racing、6 Arcade、6 GoKart。
4. Go-kart 支持 10/15/30/60 分钟，Arcade 支持 30/60/90/120 分钟。其他设施保持原有时长。

004 可重复运行，不会删除预约或账号；已有设施 ID 和付款记录保留。自定义设施超过目标数量时也会保留。你仍使用已有项目和原来的登录账号，不需重新建立 Supabase。

3D 参观已拆成 L1 电竞、L2 桌球、G 休闲及户外赛车场，模型离线运行；只有新增可预约设施需要这次云端 SQL 升级。选择楼层或使用 Change floor 电梯菜单进入另一层，摇杆在每一层自由移动。

1. 打开你已经创建的 Supabase **Mini-Project**。
2. 左边选择 **SQL Editor → New query**。
3. 打开本项目 `supabase/001_ctfly.sql`，复制全部内容进去，点击 **Run**。
4. 看到 Success 后，数据库、权限和示例商品/座位已经建立。
5. 新建另一条 query，运行 `supabase/003_session_clock.sql`，让云端每分钟结束到时的 session。

`001` 是首次安装脚本，只运行一次。它不会删除 Python 的 SQLite，但不要把它重复运行到已经安装好的 CTFLY 数据库。出现错误就把错误文字给我，不要删除现有表来解决。

**无需建立 todos table，也无需复制 Supabase 网站的示例 MainActivity。** URL 和 publishable key 已放在项目里。所有业务表启用 RLS，客户端没有直接写表权限，写入经过服务器校验。

## 2. 邮箱验证码（需要你配置）

App 已经实现：邮箱密码登录、18+ 注册、邮箱验证码、忘记密码验证码、新密码、密码小眼睛。

在 Supabase Authentication 的邮件模板中：

- **Confirm sign up**：粘贴 `supabase/email-confirmation.html`。
- **Reset password**：粘贴 `supabase/email-recovery.html`。
- 保持 Email provider 和邮箱确认开启。模板使用 `{{ .Token }}`，用户将验证码填回 App。
- 需要给普通用户发信时，配置 **Custom SMTP**（邮件服务商提供的 SMTP host、port、username、password 和已验证发件地址）。Supabase 默认邮件服务限制收件人及频率，不能当成公开上线的邮件服务。
- 验证码的有效期和服务端限流使用 Supabase Auth 设置。建议 OTP 有效期设为 10 分钟；保留发送频率限制。

正式邮件服务没准备好时，可以先在个人作业测试项目临时关闭 Confirm email，用邮箱密码注册来测试核心模块；这时邮箱未经验证，验证码流程也没有完成验证。上线前应重新开启并配置 SMTP。

不用一直打开 Gmail，不用你的电脑一直开着。不要把 SMTP 密码或 service_role key 放进 APK。

## 3. 在 Android Studio 打开

1. Android Studio → **Open** → 选择 `CTFLY-Android` 文件夹。
2. 等待 Gradle Sync。若提示 Android SDK 35 / Build Tools 缺失，允许安装。
3. Gradle JDK 使用 Android Studio 自带的 Embedded JDK（17 或以上；本机使用 21）。
4. 工具栏选择 **app（顾客）** 或 **staff（员工）**，选择 Emulator 或 Android 手机，按 **▶ Run**。

不用另建 Empty Activity。支持 Android 8.0（API 26）及以上；不支持 iPhone。不同尺寸、密度、分屏和横竖屏按当前可用宽度调整布局。不能承诺每一部手机都经过测试。

首次运行如果看到 “Cloud setup is pending”，说明第 1 步还没有完成。网络错误会显示提示，不会伪造预约成功。运行 SQL 后点刷新。

## 4. 创建 Customer / Staff / Admin

Python 的 `admin / Admin@123` 等本地演示账号不会自动变成云端账号。

1. 用真实邮箱在 **CTFLY Gaming Cafe → Join CTFLY** 注册。生日必须 18+。已有账号不用重新注册。
2. 普通顾客到这里就完成。
3. 管理员：打开 `supabase/002_staff_roles.sql`，将 `REPLACE_WITH_YOUR_ADMIN_EMAIL` 换成刚注册的邮箱，在 SQL Editor 运行。
4. 员工：另注册一个邮箱，再按同一个文件末尾的 staff SQL 修改角色。
5. 顾客版退出账号，打开独立的 **Staff CTFLY**，用对应邮箱和自己注册的密码登录。

公开注册无法选择或伪造 staff/admin 角色。员工只看到 Operations、My shifts、Handover 和 Equipment；管理员另外有右上角管理按钮。

## 5. 付款和 QR 现在怎么测试

目前可实际运行的是 **柜台现金收款记录**：

1. Customer 预约 PC / Pool 等空间。
2. Staff → Operations → Sessions，核实预约并选择收款。页面展示折扣后实际应收金额。
3. 收到现金后确认；服务器生成收据、累计积分，顾客得到 QR。
4. 到预约开始时间，Staff 扫 QR → 核验付款、时间、未使用状态 → 开始 session。
5. 到时自动完成。迟到仍以原预约结束时间为准；提前结束不退套餐费。

App 带有 Google Code Scanner；有 Google Play 服务的设备可扫描。扫描器不可用的设备可手动输入顾客 entry code。

**真实 FPX / TNG 尚未接通。** 需要你申请支付平台商户/测试账号和收款资料，之后才能加入托管支付页、签名回调、退款与对账。当前不会通过点击 TNG 按钮假装收款成功。

**Google 账号选择登录尚未接入。** 之前 Python 版已移除该入口，当前使用邮箱密码。重新加入需 Google OAuth 配置和 Android 签名配置；由于 Google 首次登录不提供生日，仍需完善 18+ 资料流程。

QR 到时结束的是 CTFLY 的 session，不能直接锁住网吧 Windows PC。自动锁机要在网吧电脑安装另外的控制程序。

## 已提供的功能

- 顾客版 **Discover → Walk through CTFLY**，或顶部立方体按钮：打开第一人称 3D 场馆。左摇杆移动，拖动画面转头，顶部按钮直达入口、柜台、PC 区、VIP 房、桌球区、Console、Café 和洗手间；地图按钮显示当前位置。支持横竖屏，退出后再次进入保留视角与位置。这是按功能设计的虚拟概念场馆，没有真实店面平面图或实景扫描；不会修改预约或收费。场景随 APK 安装，进入参观不需要加载网上模型。

- PC / VIP 和 Snooker、Pool、Console、Racing 预约、冲突检测、容量校验、取消、员工扫码入场、倒计时。
- 独立商品照片、分类搜索、收藏、购物袋、复购、柜台/活动座位配送、库存扣减/回补、订单处理、管理员补货。
- 赛事报名、收费、满员拦截、单淘汰对阵、bye 自动晋级、管理员确认胜者。
- 会员资料、等级折扣、积分、兑换券、账单及分享收据。
- 员工班次、上下班打卡、迟到/早退/工时、请假、任务、每日 checklist、交接确认、设备报修/认领/解决。
- 管理员空间/商品/分类/赛事/奖励管理、员工排班、会员停用、营收/销量/库存/出勤摘要、分享营收数据。

手机使用底部导航；宽度达到 840dp 的平板/窗口使用侧边导航和顾客双栏信息。表单与购物篮使用 SavedStateHandle，密码仅保留在内存 ViewModel，旋转不清空；系统完全杀死进程后密码需要重新输入。登录 token 使用 Android Keystore 加密保存。

## 迁移边界

这是新云端版本。旧 Python 项目、SQLite 数据和账号均保留；没有将旧顾客资料上传到云端。PC 和娱乐设施整合为 resources，预约整合为 bookings，身份交给 Supabase Auth。旧 SQLite ERD 不能直接作为新版本 ERD。

管理员可生成每周排班（支持跨午夜、整周冲突回滚），作废尚未履行的柜台付款，并由员工收取额外现金延长已付款 session。作废会回退积分与累计消费，但已消费服务、已分配的追加时间和已花掉的积分不会被自动撤销。退现金需要员工实际操作；未接通线上退款。营收与收据可以分享，旧桌面每个 CSV 报表的格式并未原样保留。

## 官方配置参考

### 2026-10-09：1.5 高清参观更新

直接安装新版 Customer APK，或 Android Studio 打开原项目运行 `app`。默认 Ultra 提高清晰度；3D 页面右上角画质图标可选择 Ultra、Balanced、Smooth，较弱设备移动卡顿时选后两种。展开图标会收起楼层和地点列表，给 3D 画面更多空间。画质、位置和视角会在旋转/Activity 重建后保留。这次只更新画面，不需要新 SQL；之前已运行 004 的不用重复运行。

### 2026-10-08：3D 参观画面更新

Customer 版本 1.4 保留第一人称摇杆和位置保存，并拆成四个独立楼层/区域。增加石材、金属、桌布、皮革、柏油与地毯细节、专属灯光和抗锯齿。模型已预先打包，直接运行 `app` 即可参观；新增预约设施按本文前面的升级步骤运行 004。它是 CTFLY 的 3D 概念场馆，仍有实时模型感，并非实景扫描。

- [Android Compose 项目](https://developer.android.com/develop/ui/compose/setup)
- [Supabase 邮件模板与 Token](https://supabase.com/docs/guides/auth/auth-email-templates)
- [Supabase Custom SMTP](https://supabase.com/docs/guides/auth/auth-smtp)
- [Supabase Cron](https://supabase.com/docs/guides/cron/quickstart)
