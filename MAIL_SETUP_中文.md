# Gmail 收据自动寄送设置

预约、点餐或比赛报名完成测试付款后，服务器会生成收据，并排队寄到顾客注册账号的邮箱。邮件服务与模拟付款分开：邮件可以真的寄出，TNG / FPX / 银行卡付款仍然不会扣钱。

数据库、私有寄信队列、每分钟自动任务、`club-services` 函数已经部署到你自己的项目 `shfatnjwpdyxdbehvjil`。**现在还没有配置发信邮箱，所以不会寄出邮件。** App 内查看、打开和分享 PDF 已经可以使用；不用再复制 SQL，也不用开另一个 Supabase 账号。

## 你说的 16 位是什么

是 Gmail 的 **16 位应用专用密码（App password）**，可能是字母，不是必须 16 个数字。它与平时的 Google 登录密码不同。不要把它发到聊天，也不要放进 APK 或源码。

1. 准备一个用于学校项目发收据的 Gmail 邮箱。
2. 登录这个 Google 账号，启用两步验证。
3. 打开 [Google 应用专用密码](https://myaccount.google.com/apppasswords)，创建一个名字为 CTFLY Receipt 的应用密码。
4. 打开 [你的 Supabase → Edge Functions → Secrets](https://supabase.com/dashboard/project/shfatnjwpdyxdbehvjil/functions/secrets)。添加：
   - `SMTP_USER`：用于发信的完整 Gmail 地址。
   - `SMTP_APP_PASSWORD`：刚创建的 16 位应用专用密码；空格会自动去掉。
5. 保存后，自动任务下一次运行会识别配置，并继续处理此前因未配置而暂停的收据。通常每分钟检查一次；实际到达时间由邮件服务决定。
6. 使用自己的顾客账号完成一次测试付款，再查看注册邮箱，包括垃圾邮件文件夹。App 的收据页显示寄送状态；服务端接受后才显示已发送。

如果 Google 没有显示应用密码入口，参阅 [Google 的应用密码说明](https://support.google.com/mail/answer/185833)。不是去修改 Supabase Auth 的登录密码；这两个变量专供收据发信函数使用，原有注册验证码设置保持独立。

默认已使用 `smtp.gmail.com`、465 端口和 TLS，不用再填主机/端口。只有更换 SMTP 服务时才设置 `SMTP_HOST` 和 `SMTP_PORT`。587 使用 STARTTLS。

也支持 Resend：设置 `RESEND_API_KEY` 和 `RECEIPT_FROM`（经该服务验证的发信地址）后优先使用 Resend。无需同时配置两套。

## 收据和寄送状态

- 收据包含完整购买内容、设备/活动、日期（含年份）、开始/结束时间、分钟数、人数、逐项价格、优惠、总额、方式、参考编号和测试标记。
- 新付款自动排队，App 关闭后仍由服务器处理。付款前的预约保留不算已付收据。
- 旧收据不会突然批量寄出。顾客可以在旧收据页按「寄至注册邮箱」申请寄送。
- 已发出的收据不重复自动寄送。寄送中断且结果不确定时显示 Unknown，需先核对邮箱，避免重复发信。
- Sent 表示发信服务接受邮件，不能保证一定进入 Gmail 主收件箱。
- 邮件附带正式 PDF；如果字体下载或 PDF 生成失败，仍会寄完整、排版好的 HTML 收据。App 本机 PDF 不依赖服务器字体下载。
- 含中文的邮件 PDF 为保证字形完整，会嵌入字体，文件约 14 MB；普通英文 PDF 小得多。App 本机 PDF 使用系统字体，较小。

本轮已验证排版、中文、字段、权限、自动任务和未配置状态；**还没有使用真实 SMTP 密码寄过邮件**。需完成上述两项 Secrets 配置后，在自己的邮箱实际验证一次。
