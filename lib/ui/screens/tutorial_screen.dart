import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/assistant_provider.dart';
import '../../theme/app_colors.dart';
import 'main_navigation_screen.dart';

/// Walkthrough of the app. Shown automatically on the first launch (then [firstRun] opens the
/// app when done or skipped), and again any time from Settings → شرح التطبيق.
class TutorialScreen extends StatefulWidget {
  final bool firstRun;

  const TutorialScreen({super.key, this.firstRun = false});

  @override
  State<TutorialScreen> createState() => _TutorialScreenState();
}

class _Page {
  final IconData icon;
  final Color color;
  final String title;
  final String intro;
  final List<(String, String)> points; // (bold lead, explanation)

  const _Page({required this.icon, required this.color, required this.title, required this.intro, required this.points});
}

/// A row of the colour legend, drawn like the real badge.
class _BadgeSample {
  final Color color;
  final bool filled;
  final String symbol;
  final String text;

  const _BadgeSample(this.color, this.filled, this.symbol, this.text);
}

const _pages = <_Page>[
  _Page(
    icon: Icons.local_taxi_rounded,
    color: AppColors.emeraldPrimary,
    title: "أهلاً بيك في The Driver",
    intro: "التطبيق بيقولّك في ثانية: الطلب ده هيكسّبك ولا هيخسّرك؟ من غير ما تحسب في دماغك.",
    points: [
      ("فوق إن درايف:", "علامة صغيرة على كل طلب فيها صافي ربحك بعد البنزين والعمولة والاستهلاك."),
      ("جوه التطبيق:", "حاسبة للعروض، متابعة الوردية والهدف اليومي، ومصاريف عربيتك."),
      ("كل الحسابات على موبايلك:", "مفيش حاجة بتتبعت برّه، إلا نص العناوين لو فعّلت «تقدير المسافة من العناوين»."),
    ],
  ),
  _Page(
    icon: Icons.checklist_rounded,
    color: AppColors.accentCyan,
    title: "الإعداد في 3 خطوات",
    intro: "من تاب «المساعد» تحت، كمّل الخطوات بالترتيب. كل خطوة بتفتحلك صفحة الإعدادات المطلوبة وترجعك.",
    points: [
      ("1. قراءة شاشة إن درايف:", "عشان التطبيق يقرا السعر والمسافة والعناوين من كروت الطلبات."),
      ("2. العرض فوق التطبيقات:", "عشان العلامة تظهر فوق إن درايف."),
      ("3. تشغيل المساعد:", "بيفضل شغال في الخلفية، وتقدر توقفه من الإشعار في أي وقت."),
    ],
  ),
  _Page(
    icon: Icons.bubble_chart_rounded,
    color: AppColors.emeraldPrimary,
    title: "اقرا العلامة في نظرة",
    intro: "الرقم = صافي ربحك من الطلب. اللون والرمز اللي في الركن بيقولولك الحكم:",
    points: [],
  ),
  _Page(
    icon: Icons.touch_app_rounded,
    color: AppColors.accentAmber,
    title: "اضغط على العلامة للتفاصيل",
    intro: "بتفتح بطاقة فيها كل حاجة عن الطلب، وتقفلها بـ «✕ تصغير» أو بلمسة برّاها.",
    points: [
      ("عرض الراكب والمزايدة المقترحة:", "السعر العادل لمشوارك حسب تكاليفك وهدفك."),
      ("صافي الربح والمسافات:", "الوصول + المشوار، والرقم اللي عليه ≈ يعني تقريبي."),
      ("مناطق وعملاء:", "زرار واحد يضيف المنطقة أو العميل للمفضلة أو المحظورة."),
    ],
  ),
  _Page(
    icon: Icons.radio_button_checked,
    color: AppColors.accentPurple,
    title: "المزايدة الذكية",
    intro: "لما تفتح طلب، إن درايف بيعرض أزرار بأسعار مقترحة. التطبيق بيحط فوق كل زر دايرة فيها صافي ربحك.",
    points: [
      ("أفضل عرض عليه نجمة ★:", "أقل سعر بيحقق هدفك، عشان الراكب يوافق أسرع. مش لازم يكون الأعلى."),
      ("لو مفيش سعر بيحقق هدفك:", "النجمة بتروح للأعلى ربحاً."),
      ("تقدر تخلّيه يضغط بنفسه:", "من الإعدادات ← العلامة العائمة ← المزايدة (اختياري، وبتحذير)."),
    ],
  ),
  _Page(
    icon: Icons.directions_car_rounded,
    color: AppColors.accentCyan,
    title: "عربيتك وتكاليفك الحقيقية",
    intro: "كل ما الأرقام تبقى أدق، الحكم على الطلب يبقى أصح.",
    points: [
      ("تاب «المركبة»:", "ضيف عربيتك واستهلاكها وسعر لتر البنزين، وسجّل كل تفويلة وغيار زيت وقطعة غيار."),
      ("الاستهلاك بيتظبط لوحده:", "من متوسط آخر تفويلات عربيتك."),
      ("الإعدادات ← التسعير:", "عمولة إن درايف، هدف الربح لكل كم، وتسعير مسافة الوصول."),
    ],
  ),
  _Page(
    icon: Icons.insights_rounded,
    color: AppColors.emeraldPrimary,
    title: "الوردية والحاسبة",
    intro: "تابع يومك وقارن أي عرض قبل ما تقبله.",
    points: [
      ("تاب «الوردية»:", "ابدأ الوردية، وشوف إيرادك قدام هدفك اليومي وربحك في الساعة."),
      ("تاب «الحاسبة»:", "حط السعر والمسافات، وشوف الحكم والمزايدات المقترحة، واحفظ الرحلة في السجل."),
      ("تاب «التحليلات»:", "كل طلب بيظهرلك بيتسجل لوحده. هتعرف ساعات الذروة، وأحسن وقت ومكان تشتغل فيه، وأكتر المشاوير، وتقدر تشارك السجل كملف Excel."),
      ("قيّم رحلتك:", "بعد كل رحلة مقبولة هيوصلك إشعار تقيّمها، والتقييم بيدخل في التوصيات."),
    ],
  ),
  _Page(
    icon: Icons.lightbulb_rounded,
    color: AppColors.warningYellow,
    title: "نصايح مهمة",
    intro: "حاجات هتوفّر عليك وقت:",
    points: [
      ("تطبيقات البنوك (زي إنستاباي):", "أوقف «قراءة الشاشة» من الإشعار أو الإعدادات السريعة قبل ما تفتحها، وشغّلها تاني بعدين."),
      ("القبول التلقائي والأتمتة:", "مقفولين افتراضياً. فعّلهم بس لو فاهم المخاطر على حسابك في إن درايف."),
      ("الشرح ده موجود دايماً:", "من الإعدادات ← شرح التطبيق."),
    ],
  ),
];

const _badgeSamples = <_BadgeSample>[
  _BadgeSample(Color(0xFF22C55E), true, "✓", "أخضر مصمت: ربح ممتاز، مرة ونص هدفك أو أكتر"),
  _BadgeSample(Color(0xFF4ADE80), false, "✓", "إطار أخضر: عرض رابح بيحقق هدفك"),
  _BadgeSample(Color(0xFFF59E0B), false, "!", "برتقالي: رابح بس أقل من هدفك، زايد"),
  _BadgeSample(Color(0xFFF87171), false, "✕", "أحمر: خسارة، أو منطقة أو عميل محظور"),
  _BadgeSample(Color(0xFF94A3B8), false, "؟", "رمادي: المسافة مش مقروءة، راجع الطلب"),
  _BadgeSample(Color(0xFF7C8CFF), false, "★", "نجمة زرقا: منطقة أو عميل مميز"),
];

class _TutorialScreenState extends State<TutorialScreen> {
  final _controller = PageController();
  int _index = 0;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _finish() async {
    final provider = context.read<AssistantProvider>();
    await provider.markTutorialSeen();
    if (!mounted) return;
    if (widget.firstRun) {
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const MainNavigationScreen()));
    } else {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final last = _index == _pages.length - 1;
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(Insets.lg, Insets.sm, Insets.sm, 0),
              child: Row(
                children: [
                  Text("${_index + 1} / ${_pages.length}",
                      style: const TextStyle(color: AppColors.textSecondary, fontSize: 13, fontWeight: FontWeight.w600)),
                  const Spacer(),
                  if (!last) TextButton(onPressed: _finish, child: const Text("تخطي الشرح")),
                ],
              ),
            ),
            Expanded(
              child: PageView.builder(
                controller: _controller,
                itemCount: _pages.length,
                onPageChanged: (i) => setState(() => _index = i),
                itemBuilder: (_, i) => _PageView(page: _pages[i], showBadges: i == 2),
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                for (var i = 0; i < _pages.length; i++)
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    width: i == _index ? 22 : 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: i == _index ? AppColors.emeraldPrimary : AppColors.surfaceHigh,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(Insets.lg),
              child: Row(
                children: [
                  if (_index > 0)
                    OutlinedButton(
                      onPressed: () => _controller.previousPage(
                          duration: const Duration(milliseconds: 250), curve: Curves.easeOut),
                      child: const Text("السابق"),
                    ),
                  const Spacer(),
                  FilledButton(
                    onPressed: last
                        ? _finish
                        : () => _controller.nextPage(duration: const Duration(milliseconds: 250), curve: Curves.easeOut),
                    child: Text(last ? (widget.firstRun ? "يلا نبدأ" : "تمام") : "التالي"),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PageView extends StatelessWidget {
  final _Page page;
  final bool showBadges;

  const _PageView({required this.page, required this.showBadges});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(Insets.xl, Insets.xl, Insets.xl, Insets.lg),
      children: [
        Center(
          child: Container(
            width: 84,
            height: 84,
            decoration: BoxDecoration(color: page.color.withValues(alpha: 0.14), shape: BoxShape.circle),
            child: Icon(page.icon, size: 44, color: page.color),
          ),
        ),
        const SizedBox(height: Insets.xl),
        Text(page.title,
            textAlign: TextAlign.center, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, height: 1.3)),
        const SizedBox(height: Insets.md),
        Text(page.intro,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.textSecondary, fontSize: 15, height: 1.6)),
        const SizedBox(height: Insets.xl),
        if (showBadges)
          for (final b in _badgeSamples) _BadgeRow(sample: b),
        for (final (lead, text) in page.points)
          Padding(
            padding: const EdgeInsets.only(bottom: Insets.md),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 7),
                  child: Container(width: 7, height: 7, decoration: BoxDecoration(color: page.color, shape: BoxShape.circle)),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text.rich(
                    TextSpan(children: [
                      TextSpan(text: "$lead ", style: const TextStyle(fontWeight: FontWeight.w700)),
                      TextSpan(text: text, style: const TextStyle(color: AppColors.textSecondary)),
                    ]),
                    style: const TextStyle(fontSize: 14.5, height: 1.6),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _BadgeRow extends StatelessWidget {
  final _BadgeSample sample;

  const _BadgeRow({required this.sample});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: Insets.md),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: sample.filled ? sample.color : const Color(0xFF0B0F19),
              shape: BoxShape.circle,
              border: Border.all(color: sample.color, width: 2.5),
            ),
            child: Text(
              sample.symbol,
              style: TextStyle(
                color: sample.filled ? const Color(0xFF0B0F17) : sample.color,
                fontWeight: FontWeight.w900,
                fontSize: 16,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(child: Text(sample.text, style: const TextStyle(fontSize: 14, height: 1.5))),
        ],
      ),
    );
  }
}
