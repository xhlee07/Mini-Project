from pathlib import Path

rows = """TEST MODE · NO MONEY CHARGED	测试模式 · 不会扣款	MOD UJIAN · TIADA CAJ WANG
TNG, online banking and cards are simulated. Do not enter real payment details.	TNG、网银和银行卡均为模拟付款，请勿输入真实付款资料。	TNG, perbankan dalam talian dan kad disimulasikan. Jangan masukkan maklumat pembayaran sebenar.
Pay in test mode	测试付款	Bayar dalam mod ujian
Checkout hold: %s	付款保留时间：%s	Tempoh pegangan: %s
Test checkout	测试结账	Daftar keluar ujian
Awaiting payment	等待付款	Menunggu pembayaran
Demo points: %s	测试积分：%s	Mata demo: %s
Subtotal	小计	Subjumlah
Member discount	会员折扣	Diskaun ahli
Total	总额	Jumlah
Reload checkout	重新加载结账	Muat semula daftar keluar
Choose a test payment method	选择测试付款方式	Pilih kaedah pembayaran ujian
Touch ’n Go eWallet · test	Touch ’n Go 电子钱包 · 测试	Touch ’n Go eWallet · ujian
Online banking (FPX) · test	网上银行（FPX）· 测试	Perbankan dalam talian (FPX) · ujian
Debit / credit card · test	借记卡／信用卡 · 测试	Kad debit / kredit · ujian
Continue to test payment	继续测试付款	Teruskan pembayaran ujian
%s simulator	%s 付款模拟器	Simulator %s
Confirm a simulated payment. Your wallet, bank and card will not be charged.	确认模拟付款。你的钱包、银行和银行卡均不会扣款。	Sahkan pembayaran simulasi. Dompet, bank dan kad anda tidak akan dicaj.
Still processing in the simulator. Complete or cancel this demo below.	模拟器正在处理中。你可以在下方完成或取消这次演示。	Masih diproses dalam simulator. Lengkapkan atau batalkan demo di bawah.
Confirm test payment	确认测试付款	Sahkan pembayaran ujian
Demo controls	演示控制	Kawalan demo
Simulate failure	模拟付款失败	Simulasikan kegagalan
Simulate processing	模拟处理中	Simulasikan pemprosesan
Cancel test payment	取消测试付款	Batalkan pembayaran ujian
Test payment successful	测试付款成功	Pembayaran ujian berjaya
No money was charged. Your demo order is confirmed.	没有扣款。你的演示订单已确认。	Tiada wang dicaj. Pesanan demo anda disahkan.
Request test refund	申请测试退款	Mohon bayaran balik ujian
Test refund completed	测试退款完成	Bayaran balik ujian selesai
Test payment failed	测试付款失败	Pembayaran ujian gagal
Checkout hold expired	付款保留时间已过	Tempoh pegangan tamat
Test payment cancelled	测试付款已取消	Pembayaran ujian dibatalkan
No real money moved.	没有真实资金转移。	Tiada wang sebenar dipindahkan.
Retry test checkout	重试测试付款	Cuba semula pembayaran ujian
Back to your plans	返回我的预约与订单	Kembali ke rancangan anda
Test transactions	测试交易	Transaksi ujian
Resume test payment	继续测试付款	Sambung pembayaran ujian
Payment details	付款详情	Butiran pembayaran
No test transactions yet	暂时没有测试交易	Belum ada transaksi ujian
Test payments will appear here.	测试付款记录将显示在这里。	Pembayaran ujian akan dipaparkan di sini.
Payments	付款记录	Pembayaran
Test payment	测试付款	Pembayaran ujian
Complete payment within the checkout hold.	请在保留时间内完成付款。	Lengkapkan pembayaran dalam tempoh pegangan.
Complete test checkout within 10 minutes. No real money will be charged. Arriving late does not extend your end time.	请在 10 分钟内完成测试付款，不会扣款。迟到不会延长结束时间。	Lengkapkan pembayaran ujian dalam 10 minit. Tiada wang sebenar dicaj. Ketibaan lewat tidak memanjangkan masa tamat.
Complete test checkout within 10 minutes. Unpaid orders will not be prepared.	请在 10 分钟内完成测试付款。未付款订单不会开始制作。	Lengkapkan pembayaran ujian dalam 10 minit. Pesanan belum dibayar tidak akan disediakan.
This simulates a refund and releases an unused booking or order. No real money will move.	这会模拟退款，并释放尚未使用的预约或订单。没有真实资金转移。	Ini mensimulasikan bayaran balik dan melepaskan tempahan atau pesanan yang belum digunakan. Tiada wang sebenar dipindahkan.
Services already started or prepared cannot be refunded here.	已经开始或制作的服务无法在这里退款。	Perkhidmatan yang telah bermula atau disediakan tidak boleh dibayar balik di sini.
Confirm test refund	确认测试退款	Sahkan bayaran balik ujian
Demo membership	演示会员积分	Keahlian demo
Demo points cannot be exchanged for real rewards.	测试积分不能兑换真实奖励。	Mata demo tidak boleh ditukar dengan ganjaran sebenar.
Pending	等待确认	Menunggu
Processing	处理中	Sedang diproses
Succeeded	成功	Berjaya
Failed	失败	Gagal
Expired	已过期	Tamat tempoh
Refunded	已退款	Dibayar balik
Test mode. No money will be charged.	测试模式，不会扣款。	Mod ujian. Tiada wang akan dicaj.
Test mode. No money was charged.	测试模式，没有扣款。	Mod ujian. Tiada wang dicaj.
Test payment already refunded.	此测试付款已经退款。	Pembayaran ujian ini sudah dibayar balik.
Already paid, cancelled or checkout hold expired.	已付款、已取消或保留时间已过。请返回查看订单状态。	Sudah dibayar, dibatalkan atau tempoh pegangan tamat. Semak status pesanan.
Amount changed. Reload checkout.	金额已改变，请重新加载结账。	Jumlah berubah. Muat semula daftar keluar.
This service has already started or been fulfilled. Test refund unavailable.	此服务已经开始或完成，无法测试退款。	Perkhidmatan telah bermula atau selesai. Bayaran balik ujian tidak tersedia.
Test checkout setup is not available. Refresh and try again.	测试付款尚未配置，请刷新后重试。	Pembayaran ujian belum tersedia. Muat semula dan cuba lagi.
Registered. Complete test checkout.	已登记，请完成测试付款。	Berdaftar. Lengkapkan pembayaran ujian.
"""
path = Path('app/src/main/assets-ui/languages.tsv')
original = path.read_text(encoding='utf-8')
keys = {row.split('\t')[0] for row in original.splitlines()}
added = [row for row in rows.splitlines() if row.split('\t')[0] not in keys]
path.write_text(original.rstrip('\n')+'\n'+'\n'.join(added)+'\n',encoding='utf-8')
print(f'Added {len(added)} payment translations without replacing existing entries.')
