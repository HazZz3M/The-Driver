// inDrive Assistant Simulator Engine

// Audio Synthesizer for alerts & chimes
const AudioFx = {
  ctx: null,
  init() {
    if (!this.ctx) {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      if (AudioContext) this.ctx = new AudioContext();
    }
  },
  playSuccess() {
    this.init();
    if (!this.ctx) return;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.frequency.setValueAtTime(587.33, this.ctx.currentTime); // D5
    osc.frequency.setValueAtTime(880, this.ctx.currentTime + 0.1); // A5
    gain.gain.setValueAtTime(0.15, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.35);
    osc.start();
    osc.stop(this.ctx.currentTime + 0.35);
  },
  playWarning() {
    this.init();
    if (!this.ctx) return;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'sawtooth';
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    osc.frequency.setValueAtTime(220, this.ctx.currentTime);
    gain.gain.setValueAtTime(0.2, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.3);
    osc.start();
    osc.stop(this.ctx.currentTime + 0.3);
  }
};

// State Store
const Store = {
  settings: {
    fuelConsumption: 8.5,
    fuelPrice: 15.25,
    commissionPercent: 10.0,
    wearPerKm: 1.2,
    targetDailyEarnings: 1500.0,
    minProfitKm: 8.0,
    currency: "EGP"
  },
  inputs: {
    pickupKm: 2.0,
    tripKm: 9.5,
    fare: 85.0,
    minutes: 24
  },
  shift: {
    active: true,
    startTime: Date.now() - (1000 * 60 * 135), // 2h 15m ago for demo
    trips: [
      { id: 1, fare: 110, pickupKm: 1.8, tripKm: 12.4, fuelCost: 18.4, netProfit: 68.2, time: "02:15 PM" },
      { id: 2, fare: 65, pickupKm: 0.8, tripKm: 6.2, fuelCost: 9.1, netProfit: 41.5, time: "03:10 PM" },
      { id: 3, fare: 95, pickupKm: 2.2, tripKm: 10.0, fuelCost: 15.8, netProfit: 57.7, time: "04:05 PM" }
    ],
    expenses: [
      {
        id: 1,
        category: "FUEL",
        liters: 36,
        pricePerLiter: 15.25,
        kmSinceLast: 420,
        consumption: 8.57,
        costPerKm: 1.31,
        amount: 549.0,
        desc: "تفويلة بنزين 92 (محطة شيل)",
        time: "01:30 PM"
      },
      {
        id: 2,
        category: "OIL_CHANGE",
        oilPricePerLiter: 320,
        oilVolumeMl: 4000,
        oilLifespanKm: 5000,
        costPerKm: 0.26,
        amount: 1280.0,
        desc: "غيار زيت شيل الترا 5W-40 وفلتر أصلي",
        time: "أمس"
      }
    ]
  },
  currentTab: 'calc',
  activeMode: 'app', // 'app' or 'indrive-sim'
  selectedBidFare: null,
  simTimerInterval: null,
  simTimeRemaining: 15
};

// Load saved settings if any
try {
  const saved = localStorage.getItem('indrive_assistant_state');
  if (saved) {
    const parsed = JSON.parse(saved);
    if (parsed.settings) Store.settings = { ...Store.settings, ...parsed.settings };
    if (parsed.shift) Store.shift = { ...Store.shift, ...parsed.shift };
  }
} catch (e) {
  console.warn(e);
}

function persistState() {
  try {
    localStorage.setItem('indrive_assistant_state', JSON.stringify({
      settings: Store.settings,
      shift: Store.shift
    }));
  } catch (e) {}
}

// Calculation Engine
function analyzeFare(pickupKm, tripKm, fare, settings) {
  const totalKm = Math.max(0.1, pickupKm + tripKm);
  const minutes = Math.max(1, Math.round(totalKm * 2.4));

  // Fuel cost
  const fuelLiters = totalKm * (settings.fuelConsumption / 100.0);
  const fuelCost = fuelLiters * settings.fuelPrice;

  // inDrive Commission
  const commissionCost = fare * (settings.commissionPercent / 100.0);

  // Wear and Tear
  const wearCost = totalKm * settings.wearPerKm;

  // Total operating costs & net profit
  const totalOperatingCost = fuelCost + commissionCost + wearCost;
  const netProfit = fare - totalOperatingCost;
  const profitPerKm = netProfit / totalKm;
  const profitPerHour = (netProfit / minutes) * 60;

  // Status Evaluation
  let status = "GOOD_PROFIT";
  let statusText = "✅ رحلة رابحة ومقبولة";
  let warningNote = null;

  if (netProfit < 0) {
    status = "LOSS_DANGER";
    statusText = "⚠️ رحلة خاسرة! ستدفع من جيبك";
    warningNote = `تكلفة البنزين والعمولة (${fuelCost.toFixed(1)} ج.م) أكبر من الأجرة المعروضة!`;
  } else if (profitPerKm < settings.minProfitKm) {
    status = "POOR_MARGIN";
    statusText = "هامش ربح ضعيف";
    warningNote = `صافي الربح (${profitPerKm.toFixed(1)} ج.م/كم) أقل من حدك الأدنى المستهدف (${settings.minProfitKm} ج.م/كم).`;
  } else if (profitPerKm >= settings.minProfitKm * 1.5) {
    status = "EXCELLENT_PROFIT";
    statusText = "🔥 رحلة ممتازة وعالية الربحية!";
  }

  // Counter Bids
  const nonCommission = fuelCost + wearCost;
  const divisor = Math.max(0.01, 1 - (settings.commissionPercent / 100.0));

  // Floor / Break even
  const breakEven = Math.ceil((nonCommission / divisor) / 5) * 5;
  // Recommended
  const targetProfit = totalKm * settings.minProfitKm;
  const recRaw = (nonCommission + targetProfit) / divisor;
  const recommended = Math.max(breakEven + 10, Math.ceil(recRaw / 5) * 5);
  // Aggressive
  const aggRaw = (nonCommission + (targetProfit * 1.6)) / divisor;
  const aggressive = Math.max(recommended + 15, Math.ceil(aggRaw / 5) * 5);

  const smartBids = [
    {
      title: "أدنى سعر (Floor)",
      fare: breakEven,
      net: Math.max(0, breakEven - (breakEven * (settings.commissionPercent / 100)) - nonCommission),
      perKm: Math.max(0, (breakEven - (breakEven * (settings.commissionPercent / 100)) - nonCommission) / totalKm),
      desc: "لتغطية البنزين والعمولة دون أي خسارة.",
      tag: "MINIMUM",
      tagClass: "min"
    },
    {
      title: "السعر الموصى به (Optimal)",
      fare: recommended,
      net: recommended - (recommended * (settings.commissionPercent / 100)) - nonCommission,
      perKm: (recommended - (recommended * (settings.commissionPercent / 100)) - nonCommission) / totalKm,
      desc: "نسبة قبول عالية لدى الراكب مع هامش ربح ممتاز.",
      tag: "RECOMMENDED",
      tagClass: "rec"
    },
    {
      title: "سعر الذروة (Aggressive)",
      fare: aggressive,
      net: aggressive - (aggressive * (settings.commissionPercent / 100)) - nonCommission,
      perKm: (aggressive - (aggressive * (settings.commissionPercent / 100)) - nonCommission) / totalKm,
      desc: "مناسب لأوقات الزحام والأمطار أو الرحلات البعيدة.",
      tag: "HIGH YIELD",
      tagClass: "agg"
    }
  ];

  return {
    totalKm,
    minutes,
    fuelLiters,
    fuelCost,
    commissionCost,
    wearCost,
    totalOperatingCost,
    netProfit,
    profitPerKm,
    profitPerHour,
    status,
    statusText,
    warningNote,
    breakEven,
    recommended,
    aggressive,
    smartBids
  };
}

// UI Rendering
function render() {
  const analysis = analyzeFare(
    Store.inputs.pickupKm,
    Store.inputs.tripKm,
    Store.inputs.fare,
    Store.settings
  );

  renderCalculatorScreen(analysis);
  renderShiftDashboard();
  renderExpensesScreen();
  renderSettingsScreen();
  renderInDriveSimScreen(analysis);
  renderQuickSidebarSummary(analysis);
}

function renderCalculatorScreen(analysis) {
  const container = document.getElementById('calc-view');
  if (!container) return;

  const currency = Store.settings.currency;
  const isSelected = Store.selectedBidFare !== null;
  const finalFare = isSelected ? Store.selectedBidFare : Store.inputs.fare;

  let bannerClass = 'profitable';
  if (analysis.status === 'LOSS_DANGER') bannerClass = 'danger';
  else if (analysis.status === 'POOR_MARGIN') bannerClass = 'warning';

  container.innerHTML = `
    <!-- Profit Status Banner -->
    <div class="profit-banner ${bannerClass}">
      <div class="banner-top">
        <div class="banner-title">
          <span>${analysis.status === 'LOSS_DANGER' ? '🚨' : analysis.status === 'POOR_MARGIN' ? '⚠️' : '✨'}</span>
          <span>${analysis.statusText}</span>
        </div>
        <div class="banner-net-amount" style="color: ${analysis.netProfit >= 0 ? 'var(--neon-green)' : 'var(--loss-red)'}">
          ${analysis.netProfit >= 0 ? '+' : ''}${analysis.netProfit.toFixed(1)} ${currency}
        </div>
      </div>
      ${analysis.warningNote ? `<div class="banner-warning-msg">${analysis.warningNote}</div>` : ''}
      <div class="banner-stats-pills">
        <div class="stat-pill">صافي/كم: <strong>${analysis.profitPerKm.toFixed(1)} ${currency}</strong></div>
        <div class="stat-pill">بنزين: <strong>${analysis.fuelCost.toFixed(1)} ${currency}</strong></div>
        <div class="stat-pill">عمولة 10%: <strong>${analysis.commissionCost.toFixed(1)} ${currency}</strong></div>
        <div class="stat-pill">وقت تقريبي: <strong>${analysis.minutes} دقيقة</strong></div>
      </div>
    </div>

    <!-- Quick Distance & Fare Inputs -->
    <div class="app-card">
      <div class="card-title">
        <span>بيانات طلب الراكب</span>
        <small style="color: var(--neon-green)">Total: ${analysis.totalKm.toFixed(1)} km</small>
      </div>

      <!-- Pickup Slider -->
      <div class="slider-control">
        <div class="slider-label-row">
          <span>مسافة الوصول للراكب (Pickup):</span>
          <div class="slider-val-tag">
            <button class="step-btn" onclick="stepVal('pickupKm', -0.5)">-</button>
            <span>${Store.inputs.pickupKm.toFixed(1)} كم</span>
            <button class="step-btn" onclick="stepVal('pickupKm', 0.5)">+</button>
          </div>
        </div>
        <input type="range" min="0.2" max="12" step="0.2" value="${Store.inputs.pickupKm}" oninput="setVal('pickupKm', this.value)">
      </div>

      <!-- Trip Distance Slider -->
      <div class="slider-control">
        <div class="slider-label-row">
          <span>مسافة الرحلة (Trip Distance):</span>
          <div class="slider-val-tag">
            <button class="step-btn" onclick="stepVal('tripKm', -1)">-</button>
            <span>${Store.inputs.tripKm.toFixed(1)} كم</span>
            <button class="step-btn" onclick="stepVal('tripKm', 1)">+</button>
          </div>
        </div>
        <input type="range" min="1" max="50" step="0.5" value="${Store.inputs.tripKm}" oninput="setVal('tripKm', this.value)">
      </div>

      <!-- Fare Slider -->
      <div class="slider-control">
        <div class="slider-label-row">
          <span>عرض الراكب الحالي (Passenger Offer):</span>
          <div class="slider-val-tag">
            <button class="step-btn" onclick="stepVal('fare', -5)">-</button>
            <span>${Store.inputs.fare.toFixed(0)} ${currency}</span>
            <button class="step-btn" onclick="stepVal('fare', 5)">+</button>
          </div>
        </div>
        <input type="range" min="15" max="350" step="5" value="${Store.inputs.fare}" oninput="setVal('fare', this.value)">
      </div>
    </div>

    <!-- Operational Cost Breakdown -->
    <div class="app-card">
      <div class="card-title"><span>التكلفة التشغيلية الحقيقية للرحلة</span></div>
      <div class="cost-row">
        <span>استهلاك الوقود (${analysis.fuelLiters.toFixed(2)} لتر @ ${Store.settings.fuelPrice} ج.م)</span>
        <strong>${analysis.fuelCost.toFixed(1)} ${currency}</strong>
      </div>
      <div class="cost-row">
        <span>عمولة تطبيق إن درايف (${Store.settings.commissionPercent}%)</span>
        <strong>${analysis.commissionCost.toFixed(1)} ${currency}</strong>
      </div>
      <div class="cost-row">
        <span>إهلاك وصيانة دورية للسيارة</span>
        <strong>${analysis.wearCost.toFixed(1)} ${currency}</strong>
      </div>
      <div class="cost-divider"></div>
      <div class="cost-row" style="color: var(--text-white); font-weight: bold;">
        <span>إجمالي التكلفة الإلزامية:</span>
        <strong style="color: var(--loss-red)">${analysis.totalOperatingCost.toFixed(1)} ${currency}</strong>
      </div>
    </div>

    <!-- Smart Counter Bids -->
    <div class="app-card">
      <div class="card-title">
        <span>عروض المزايدة الذكية (Counter-Bids)</span>
        <small style="color: var(--text-muted)">اختر للمزايدة</small>
      </div>
      <div class="bids-group">
        ${analysis.smartBids.map(bid => `
          <div class="bid-chip-card ${bid.fare === Store.selectedBidFare ? 'selected' : ''} ${bid.tag === 'RECOMMENDED' ? 'recommended' : ''}" onclick="selectBid(${bid.fare})">
            <div class="bid-info">
              <h4>${bid.title} <span class="badge-tag ${bid.tagClass}">${bid.tag}</span></h4>
              <p>${bid.desc}</p>
              <p style="color: var(--neon-green); font-weight: bold; margin-top: 4px;">صافي ربح: +${bid.net.toFixed(1)} ${currency} (${bid.perKm.toFixed(1)}/كم)</p>
            </div>
            <div class="bid-fare">
              ${bid.fare}
              <small>${currency}</small>
            </div>
          </div>
        `).join('')}
      </div>
    </div>

    <!-- Action Buttons -->
    <div style="display: flex; flex-direction: column; gap: 8px;">
      <button class="btn-primary" onclick="acceptAndSaveRide(${finalFare})">
        <span>💾</span>
        <span>حفظ الرحلة (${finalFare} ${currency}) بسجل الوردية</span>
      </button>
      <button class="btn-secondary" onclick="applyOptimalBid(${analysis.recommended})">
        <span>⚡ تطبيق السعر الموصى به (${analysis.recommended} ${currency})</span>
      </button>
    </div>
  `;
}

function renderShiftDashboard() {
  const container = document.getElementById('shift-view');
  if (!container) return;

  const totalGross = Store.shift.trips.reduce((acc, t) => acc + t.fare, 0);
  const totalNet = Store.shift.trips.reduce((acc, t) => acc + t.netProfit, 0);
  const progressPct = Math.min(100, Math.round((totalGross / Store.settings.targetDailyEarnings) * 100));
  const remaining = Math.max(0, Store.settings.targetDailyEarnings - totalGross);

  container.innerHTML = `
    <!-- Header with Start/Stop Shift -->
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <div>
        <h3 style="font-size: 16px; font-weight: 800;">الوردية اليومية 📊</h3>
        <p style="font-size: 11px; color: var(--text-muted)">متابعة الإيراد والأرباح الصافية</p>
      </div>
      <button class="step-btn" style="width: auto; padding: 6px 14px; border-radius: 12px; font-weight: bold; background: ${Store.shift.active ? 'var(--loss-red)' : 'var(--neon-green)'}; color: ${Store.shift.active ? '#fff' : '#0F172A'};" onclick="toggleShiftState()">
        ${Store.shift.active ? '⏹ إنهاء الوردية' : '▶ بدء الوردية'}
      </button>
    </div>

    <!-- Daily Target Card -->
    <div class="app-card">
      <div style="display: flex; justify-content: space-between; font-size: 12px; color: var(--text-muted); margin-bottom: 6px;">
        <span>الهدف اليومي (Target)</span>
        <strong style="color: var(--neon-green);">${progressPct}% منجز</strong>
      </div>
      <div style="font-size: 26px; font-weight: 900; color: #fff; margin-bottom: 10px;">
        ${totalGross.toFixed(0)} <span style="font-size: 14px; color: var(--text-muted); font-weight: normal;">/ ${Store.settings.targetDailyEarnings} ${Store.settings.currency}</span>
      </div>
      <div style="height: 8px; background: #334155; border-radius: 4px; overflow: hidden; margin-bottom: 10px;">
        <div style="width: ${progressPct}%; height: 100%; background: var(--neon-green); transition: width 0.3s;"></div>
      </div>
      <p style="font-size: 11.5px; color: ${remaining === 0 ? 'var(--neon-green)' : 'var(--text-muted)'}">
        ${remaining === 0 ? '🏆 أحسنت! تم تحقيق الهدف وتجاوزه بنجاح!' : `متبقي ${remaining.toFixed(0)} ${Store.settings.currency} لتحقيق هدف اليوم 🎯`}
      </p>
    </div>

    <!-- Shift KPIs -->
    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
      <div class="app-card">
        <span style="font-size: 11px; color: var(--text-muted)">صافي الأرباح في جيبك</span>
        <div style="font-size: 20px; font-weight: 800; color: var(--neon-green); margin-top: 4px;">
          +${totalNet.toFixed(0)} ${Store.settings.currency}
        </div>
      </div>
      <div class="app-card">
        <span style="font-size: 11px; color: var(--text-muted)">عدد الرحلات المنجزة</span>
        <div style="font-size: 20px; font-weight: 800; color: var(--accent-cyan); margin-top: 4px;">
          ${Store.shift.trips.length} رحلة
        </div>
      </div>
    </div>

    <!-- Trips History -->
    <div class="app-card">
      <div class="card-title">
        <span>سجل رحلات الوردية (${Store.shift.trips.length})</span>
        ${Store.shift.trips.length > 0 ? `<button onclick="clearTrips()" style="background: none; border: none; color: var(--loss-red); font-size: 11px; cursor: pointer;">مسح السجل</button>` : ''}
      </div>
      ${Store.shift.trips.length === 0 ? `
        <div style="text-align: center; padding: 24px; color: var(--text-muted); font-size: 12px;">
          🚖 لا توجد رحلات مسجلة اليوم بعد
        </div>
      ` : `
        <div style="display: flex; flex-direction: column; gap: 8px;">
          ${Store.shift.trips.map(trip => `
            <div style="background: var(--surface-dark); border: 1px solid var(--surface-border); border-radius: 12px; padding: 10px 12px; display: flex; justify-content: space-between; align-items: center;">
              <div>
                <div style="font-weight: 700; font-size: 13px; color: #fff;">رحلة ${trip.time} <span style="font-size: 10px; background: rgba(0,234,128,0.15); color: var(--neon-green); padding: 2px 6px; border-radius: 4px;">${(trip.pickupKm + trip.tripKm).toFixed(1)} كم</span></div>
                <div style="font-size: 10.5px; color: var(--text-muted); margin-top: 3px;">الأجرة: ${trip.fare} ج.م | بنزين: ${trip.fuelCost.toFixed(1)} ج.م</div>
              </div>
              <div style="text-align: right;">
                <div style="font-size: 15px; font-weight: 900; color: var(--neon-green);">+${trip.netProfit.toFixed(1)}</div>
                <div style="font-size: 9.5px; color: var(--text-muted);">صافي ${Store.settings.currency}</div>
              </div>
            </div>
          `).join('')}
        </div>
      `}
    </div>
  `;
}

function renderExpensesScreen() {
  const container = document.getElementById('expenses-view');
  if (!container) return;

  const totalExpenses = Store.shift.expenses.reduce((acc, e) => acc + e.amount, 0);

  // Compute fuel refills metrics
  const fuelRefills = Store.shift.expenses.filter(e => e.category === 'FUEL' && e.consumption);
  let avgConsumption = null;
  let avgFuelCostPerKm = null;
  if (fuelRefills.length > 0) {
    avgConsumption = fuelRefills.reduce((sum, e) => sum + e.consumption, 0) / fuelRefills.length;
    avgFuelCostPerKm = fuelRefills.reduce((sum, e) => sum + e.costPerKm, 0) / fuelRefills.length;
  }

  // Compute oil changes metrics
  const oilChanges = Store.shift.expenses.filter(e => e.category === 'OIL_CHANGE' && e.costPerKm);
  let avgOilCostPerKm = null;
  if (oilChanges.length > 0) {
    avgOilCostPerKm = oilChanges.reduce((sum, e) => sum + e.costPerKm, 0) / oilChanges.length;
  }

  container.innerHTML = `
    <!-- Top Header -->
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <div>
        <h3 style="font-size: 16px; font-weight: 800;">مصاريف الوقود والزيت ⛽🛢️</h3>
        <p style="font-size: 11px; color: var(--text-muted)">حساب دقيق لاستهلاك وتكلفة الكيلومتر</p>
      </div>
    </div>

    <!-- DEDICATED ACTION BUTTONS FOR FUEL AND OIL -->
    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
      <button class="btn-primary" style="padding: 12px 8px; font-size: 13px; border-radius: 14px;" onclick="openFuelModal()">
        <span>⛽ تفويلة بنزين</span>
      </button>
      <button class="btn-primary" style="padding: 12px 8px; font-size: 13px; border-radius: 14px; background: var(--accent-cyan); color: #0F172A; box-shadow: 0 4px 14px rgba(56,189,248,0.25);" onclick="openOilModal()">
        <span>🛢️ غيار زيت</span>
      </button>
    </div>

    <!-- Real-world Metrics Card -->
    <div class="app-card">
      <div class="card-title">
        <span>مؤشرات أداء السيارة الحقيقية (من التفويلات)</span>
      </div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
        <div style="background: var(--surface-dark); padding: 10px; border-radius: 12px; border: 1px solid var(--surface-border);">
          <span style="font-size: 10.5px; color: var(--text-muted);">متوسط تكلفة الوقود/كم:</span>
          <div style="font-size: 17px; font-weight: 800; color: var(--neon-green); margin-top: 3px;">
            ${avgFuelCostPerKm ? avgFuelCostPerKm.toFixed(2) + ' ج.م/كم' : 'بانتظار تفويلة'}
          </div>
        </div>
        <div style="background: var(--surface-dark); padding: 10px; border-radius: 12px; border: 1px solid var(--surface-border);">
          <span style="font-size: 10.5px; color: var(--text-muted);">معدل الاستهلاك الفعلي:</span>
          <div style="font-size: 17px; font-weight: 800; color: var(--accent-cyan); margin-top: 3px;">
            ${avgConsumption ? avgConsumption.toFixed(1) + ' L/100km' : 'بانتظار تفويلة'}
          </div>
        </div>
      </div>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 10px; padding-top: 8px; border-top: 1px solid var(--surface-border); font-size: 11px; color: var(--text-muted);">
        <span>تكلفة الزيت لكل كم: <strong style="color: #fff;">${avgOilCostPerKm ? avgOilCostPerKm.toFixed(2) + ' ج.م/كم' : '0.26 ج.م/كم'}</strong></span>
        <span>إجمالي المصاريف: <strong style="color: var(--warning-amber);">${totalExpenses.toFixed(0)} ${Store.settings.currency}</strong></span>
      </div>
    </div>

    <!-- Expenses History List -->
    <div class="app-card">
      <div class="card-title">
        <span>سجل التفويلات وغيارات الزيت (${Store.shift.expenses.length})</span>
      </div>
      ${Store.shift.expenses.length === 0 ? `
        <div style="text-align: center; padding: 24px; color: var(--text-muted); font-size: 12px;">
          ⛽ لا توجد فواتير بنزين أو زيت مسجلة اليوم
        </div>
      ` : `
        <div style="display: flex; flex-direction: column; gap: 8px;">
          ${Store.shift.expenses.map(e => `
            <div style="background: var(--surface-dark); border: 1px solid ${e.category === 'FUEL' ? 'rgba(0,234,128,0.3)' : 'rgba(56,189,248,0.3)'}; border-radius: 12px; padding: 12px; display: flex; flex-direction: column; gap: 6px;">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <div style="font-weight: 700; font-size: 13px; color: #fff; display: flex; align-items: center; gap: 6px;">
                  <span>${e.category === 'FUEL' ? '⛽' : '🛢️'}</span>
                  <span>${e.desc || (e.category === 'FUEL' ? 'تفويلة بنزين' : 'غيار زيت')}</span>
                </div>
                <div style="font-size: 15px; font-weight: 800; color: ${e.category === 'FUEL' ? 'var(--neon-green)' : 'var(--accent-cyan)'};">
                  -${e.amount.toFixed(0)} ${Store.settings.currency}
                </div>
              </div>

              <!-- Metrics badges -->
              ${e.category === 'FUEL' && e.consumption ? `
                <div style="display: flex; gap: 6px; flex-wrap: wrap;">
                  <span class="metric-pill-badge">المسافة: <strong>${e.kmSinceLast || e.km} كم</strong></span>
                  <span class="metric-pill-badge">الاستهلاك: <strong>${e.consumption.toFixed(1)} L/100km</strong></span>
                  <span class="metric-pill-badge" style="border: 1px solid var(--neon-green);">تكلفة/كم: <strong style="color: var(--neon-green);">${e.costPerKm.toFixed(2)} ج.م</strong></span>
                </div>
              ` : ''}

              ${e.category === 'OIL_CHANGE' && e.costPerKm ? `
                <div style="display: flex; gap: 6px; flex-wrap: wrap;">
                  <span class="metric-pill-badge">الكمية: <strong>${e.oilVolumeMl} ملل</strong></span>
                  <span class="metric-pill-badge">مسافة الزيت: <strong>${e.oilLifespanKm} كم</strong></span>
                  <span class="metric-pill-badge" style="border: 1px solid var(--accent-cyan);">تكلفة الزيت/كم: <strong style="color: var(--accent-cyan);">${e.costPerKm.toFixed(2)} ج.م</strong></span>
                </div>
              ` : ''}

              <div style="font-size: 10px; color: var(--text-muted); display: flex; justify-content: space-between;">
                <span>${e.time}</span>
                <button onclick="deleteExpenseItem(${e.id})" style="background: none; border: none; color: var(--loss-red); font-size: 11px; cursor: pointer;">حذف</button>
              </div>
            </div>
          `).join('')}
        </div>
      `}
    </div>
  `;
}

function renderSettingsScreen() {
  const container = document.getElementById('settings-view');
  if (!container) return;

  container.innerHTML = `
    <h3 style="font-size: 16px; font-weight: 800;">إعدادات السيارة والتسعير ⚙️</h3>
    <p style="font-size: 11px; color: var(--text-muted); margin-bottom: 8px;">تخصيص أسعار الوقود ونسب العمولات لحساب الأرباح بدقة</p>

    <!-- Egyptian Fuel Presets -->
    <div class="app-card">
      <div class="card-title"><span>تسعير البنزين في مصر (تطبيق فوري)</span></div>
      <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px;">
        <button class="step-btn" style="width: auto; height: auto; padding: 6px; border-radius: 8px; flex-direction: column; font-size: 10px; background: ${Store.settings.fuelPrice === 15.25 ? 'var(--neon-green)' : 'var(--surface-dark)'}; color: ${Store.settings.fuelPrice === 15.25 ? '#000' : '#fff'};" onclick="setFuelPreset(15.25)">
          <strong>بنزين 92</strong>
          <small>15.25 ج.م</small>
        </button>
        <button class="step-btn" style="width: auto; height: auto; padding: 6px; border-radius: 8px; flex-direction: column; font-size: 10px; background: ${Store.settings.fuelPrice === 17.00 ? 'var(--neon-green)' : 'var(--surface-dark)'}; color: ${Store.settings.fuelPrice === 17.00 ? '#000' : '#fff'};" onclick="setFuelPreset(17.00)">
          <strong>بنزين 95</strong>
          <small>17.00 ج.م</small>
        </button>
        <button class="step-btn" style="width: auto; height: auto; padding: 6px; border-radius: 8px; flex-direction: column; font-size: 10px; background: ${Store.settings.fuelPrice === 13.75 ? 'var(--neon-green)' : 'var(--surface-dark)'}; color: ${Store.settings.fuelPrice === 13.75 ? '#000' : '#fff'};" onclick="setFuelPreset(13.75)">
          <strong>بنزين 80</strong>
          <small>13.75 ج.م</small>
        </button>
        <button class="step-btn" style="width: auto; height: auto; padding: 6px; border-radius: 8px; flex-direction: column; font-size: 10px; background: ${Store.settings.fuelPrice === 11.50 ? 'var(--neon-green)' : 'var(--surface-dark)'}; color: ${Store.settings.fuelPrice === 11.50 ? '#000' : '#fff'};" onclick="setFuelPreset(11.50)">
          <strong>سولار/غاز</strong>
          <small>11.50 ج.م</small>
        </button>
      </div>
    </div>

    <!-- Inputs -->
    <div class="app-card">
      <div class="slider-control">
        <div class="slider-label-row">
          <span>سعر لتر الوقود (${Store.settings.currency}):</span>
          <strong>${Store.settings.fuelPrice} ج.م</strong>
        </div>
        <input type="range" min="8" max="25" step="0.25" value="${Store.settings.fuelPrice}" oninput="updateSetting('fuelPrice', parseFloat(this.value))">
      </div>

      <div class="slider-control">
        <div class="slider-label-row">
          <span>معدل استهلاك الوقود (لتر / 100 كم):</span>
          <strong>${Store.settings.fuelConsumption} L/100km</strong>
        </div>
        <input type="range" min="5" max="15" step="0.5" value="${Store.settings.fuelConsumption}" oninput="updateSetting('fuelConsumption', parseFloat(this.value))">
      </div>

      <div class="slider-control">
        <div class="slider-label-row">
          <span>عمولة إن درايف (%):</span>
          <strong>${Store.settings.commissionPercent}%</strong>
        </div>
        <input type="range" min="5" max="15" step="0.5" value="${Store.settings.commissionPercent}" oninput="updateSetting('commissionPercent', parseFloat(this.value))">
      </div>

      <div class="slider-control">
        <div class="slider-label-row">
          <span>الحد الأدنى لصافي الربح المطلوب (ج.م / كم):</span>
          <strong>${Store.settings.minProfitKm} ج.م/كم</strong>
        </div>
        <input type="range" min="3" max="20" step="1" value="${Store.settings.minProfitKm}" oninput="updateSetting('minProfitKm', parseFloat(this.value))">
      </div>

      <div class="slider-control">
        <div class="slider-label-row">
          <span>الهدف المالي اليومي (${Store.settings.currency}):</span>
          <strong>${Store.settings.targetDailyEarnings} ${Store.settings.currency}</strong>
        </div>
        <input type="range" min="500" max="4000" step="100" value="${Store.settings.targetDailyEarnings}" oninput="updateSetting('targetDailyEarnings', parseFloat(this.value))">
      </div>
    </div>
  `;
}

// InDrive Screen Simulator View (Mode B)
function renderInDriveSimScreen(analysis) {
  const container = document.getElementById('indrive-sim-view');
  if (!container) return;

  const currency = Store.settings.currency;

  container.innerHTML = `
    <div class="indrive-screen-sim">
      <!-- Simulated GPS Navigation Map -->
      <div class="map-background">
        <div class="map-car-icon">🚗</div>
      </div>

      <!-- FLOATING HUD BUBBLE (Over inDrive) -->
      <div id="sim-hud-bubble" class="floating-hud-bubble" onclick="toggleSimHudExpanded()">
        <span style="font-size: 16px;">🚖</span>
        <div>
          <div class="bubble-title">inDrive HUD</div>
          <div class="bubble-profit" style="color: ${analysis.netProfit >= 0 ? 'var(--neon-green)' : 'var(--loss-red)'}">
            صافي: ${analysis.netProfit >= 0 ? '+' : ''}${analysis.netProfit.toFixed(0)} ${currency}
          </div>
        </div>
      </div>

      <!-- EXPANDED HUD OVERLAY -->
      <div id="sim-hud-expanded" class="floating-hud-expanded" style="display: none;">
        <div class="hud-expanded-header">
          <h3>⚡ حاسبة إن درايف العائمة (HUD)</h3>
          <button class="hud-close-btn" onclick="toggleSimHudExpanded()">✕</button>
        </div>

        <div style="background: rgba(0,0,0,0.4); padding: 8px 12px; border-radius: 10px; margin-bottom: 10px; font-size: 11.5px;">
          <div style="display: flex; justify-content: space-between;">
            <span>صافي الربح المتوقع:</span>
            <strong style="color: ${analysis.netProfit >= 0 ? 'var(--neon-green)' : 'var(--loss-red)'}; font-size: 14px;">
              ${analysis.netProfit >= 0 ? '+' : ''}${analysis.netProfit.toFixed(1)} ${currency} (${analysis.profitPerKm.toFixed(1)}/كم)
            </strong>
          </div>
          <div style="color: var(--text-muted); font-size: 10px; margin-top: 3px;">
            بنزين: ${analysis.fuelCost.toFixed(1)} ج.م • عمولة: ${analysis.commissionCost.toFixed(1)} ج.م
          </div>
        </div>

        <div style="display: flex; gap: 6px; margin-bottom: 8px;">
          <button class="trigger-order-btn" style="padding: 6px; text-align: center;" onclick="sendCounterOffer(${analysis.breakEven})">
            <div>أدنى سعر</div>
            <strong style="color: #fff; font-size: 13px;">${analysis.breakEven} ج.م</strong>
          </button>
          <button class="trigger-order-btn" style="padding: 6px; text-align: center; border-color: var(--neon-green); background: rgba(0,234,128,0.12);" onclick="sendCounterOffer(${analysis.recommended})">
            <div>موصى به ⭐</div>
            <strong style="color: var(--neon-green); font-size: 13px;">${analysis.recommended} ج.م</strong>
          </button>
          <button class="trigger-order-btn" style="padding: 6px; text-align: center;" onclick="sendCounterOffer(${analysis.aggressive})">
            <div>ذروة</div>
            <strong style="color: var(--accent-cyan); font-size: 13px;">${analysis.aggressive} ج.م</strong>
          </button>
        </div>
      </div>

      <!-- IN-DRIVE PASSENGER ORDER POPUP (Simulating real inDrive 15s timer) -->
      <div class="indrive-order-popup">
        <div class="order-timer-bar">
          <div id="sim-timer-fill" class="timer-fill"></div>
        </div>

        <div class="order-header">
          <div class="passenger-info">
            <div class="passenger-avatar">👤</div>
            <div>
              <div class="passenger-name">أحمد م. (الراكب)</div>
              <div class="passenger-rating">★ 4.9 (124 رحلة)</div>
            </div>
          </div>
          <div class="order-fare-tag">
            ${Store.inputs.fare} ${currency}
          </div>
        </div>

        <div class="route-info">
          <div class="route-row">
            <div class="route-pin pickup"></div>
            <span>الاستلام: المعادي (${Store.inputs.pickupKm.toFixed(1)} كم إليك)</span>
          </div>
          <div class="route-row">
            <div class="route-pin dropoff"></div>
            <span>الوجهة: التجمع الخامس (${Store.inputs.tripKm.toFixed(1)} كم مسافة الرحلة)</span>
          </div>
        </div>

        <div style="display: flex; gap: 8px;">
          <button style="flex: 1; background: #374151; color: #fff; border: none; padding: 12px; border-radius: 12px; font-weight: bold; cursor: pointer;" onclick="rejectSimOrder()">
            رفض
          </button>
          <button style="flex: 2; background: var(--neon-green); color: #0F172A; border: none; padding: 12px; border-radius: 12px; font-weight: 800; cursor: pointer;" onclick="acceptSimOrder()">
            قبول (${Store.inputs.fare} ${currency})
          </button>
        </div>
      </div>
    </div>
  `;

  startSimTimer();
}

function startSimTimer() {
  clearInterval(Store.simTimerInterval);
  Store.simTimeRemaining = 15;
  const timerFill = document.getElementById('sim-timer-fill');
  if (timerFill) timerFill.style.width = '100%';

  Store.simTimerInterval = setInterval(() => {
    Store.simTimeRemaining--;
    const pct = Math.max(0, (Store.simTimeRemaining / 15) * 100);
    const fill = document.getElementById('sim-timer-fill');
    if (fill) fill.style.width = pct + '%';

    if (Store.simTimeRemaining <= 0) {
      clearInterval(Store.simTimerInterval);
      // Auto regenerate or warn
    }
  }, 1000);
}

function toggleSimHudExpanded() {
  const expanded = document.getElementById('sim-hud-expanded');
  if (!expanded) return;
  if (expanded.style.display === 'none') {
    expanded.style.display = 'block';
  } else {
    expanded.style.display = 'none';
  }
}

function sendCounterOffer(fare) {
  AudioFx.playSuccess();
  alert(`تم إرسال عرض المزايدة المقترح (${fare} ${Store.settings.currency}) للراكب عبر الزر العائم!`);
  acceptAndSaveRide(fare);
  toggleSimHudExpanded();
}

function acceptSimOrder() {
  AudioFx.playSuccess();
  acceptAndSaveRide(Store.inputs.fare);
  alert(`تم قبول الرحلة وحفظها بسجل أرباح اليوم! 🚀`);
}

function rejectSimOrder() {
  AudioFx.playWarning();
  triggerRandomOrder('fair');
}

function renderQuickSidebarSummary(analysis) {
  const container = document.getElementById('sidebar-metrics');
  if (!container) return;

  container.innerHTML = `
    <div style="display: flex; flex-direction: column; gap: 8px; font-size: 13px;">
      <div style="display: flex; justify-content: space-between;">
        <span style="color: var(--text-muted)">المسافة الكلية:</span>
        <strong>${analysis.totalKm.toFixed(1)} كم</strong>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="color: var(--text-muted)">استهلاك الوقود:</span>
        <strong style="color: var(--warning-amber);">${analysis.fuelCost.toFixed(1)} ج.م</strong>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="color: var(--text-muted)">عمولة إن درايف:</span>
        <strong>${analysis.commissionCost.toFixed(1)} ج.م</strong>
      </div>
      <div style="display: flex; justify-content: space-between;">
        <span style="color: var(--text-muted)">صافي الربح المتوقع:</span>
        <strong style="color: ${analysis.netProfit >= 0 ? 'var(--neon-green)' : 'var(--loss-red)'}; font-size: 15px;">
          ${analysis.netProfit >= 0 ? '+' : ''}${analysis.netProfit.toFixed(1)} ج.م
        </strong>
      </div>
    </div>
  `;
}

// User Actions
window.stepVal = function(key, delta) {
  Store.inputs[key] = Math.max(0.2, Math.round((Store.inputs[key] + delta) * 10) / 10);
  Store.selectedBidFare = null;
  render();
};

window.setVal = function(key, val) {
  Store.inputs[key] = parseFloat(val);
  Store.selectedBidFare = null;
  render();
};

window.selectBid = function(fare) {
  Store.selectedBidFare = fare;
  render();
};

window.applyOptimalBid = function(fare) {
  Store.inputs.fare = fare;
  Store.selectedBidFare = fare;
  AudioFx.playSuccess();
  render();
};

window.acceptAndSaveRide = function(fare) {
  const analysis = analyzeFare(Store.inputs.pickupKm, Store.inputs.tripKm, fare, Store.settings);
  Store.shift.trips.unshift({
    id: Date.now(),
    fare: fare,
    pickupKm: Store.inputs.pickupKm,
    tripKm: Store.inputs.tripKm,
    fuelCost: analysis.fuelCost,
    netProfit: analysis.netProfit,
    time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  });
  persistState();
  AudioFx.playSuccess();
  render();
};

window.clearTrips = function() {
  if (confirm("هل تريد مسح سجل رحلات اليوم؟")) {
    Store.shift.trips = [];
    persistState();
    render();
  }
};

window.toggleShiftState = function() {
  Store.shift.active = !Store.shift.active;
  persistState();
  render();
};

window.deleteExpenseItem = function(id) {
  Store.shift.expenses = Store.shift.expenses.filter(e => e.id !== id);
  persistState();
  render();
};

// 1. FUEL REFILL MODAL LOGIC
window.openFuelModal = function() {
  const existing = document.getElementById('fuel-modal');
  if (existing) existing.remove();

  const modalHtml = `
    <div id="fuel-modal" class="modal-backdrop" onclick="if(event.target===this) closeFuelModal()">
      <div class="modal-dialog">
        <div class="modal-header">
          <h3><span>⛽</span> تسجيل تفويلة بنزين</h3>
          <button class="modal-close-btn" onclick="closeFuelModal()">✕</button>
        </div>

        <div class="modal-form-group">
          <label>كم لتر اتحط في التانك (Liters):</label>
          <input type="number" step="0.5" id="fuel-liters" class="modal-input" placeholder="مثال: 35 لتر" value="35" oninput="updateFuelModalPreview()">
        </div>

        <div class="modal-form-group">
          <label>سعر لتر البنزين (${Store.settings.currency}):</label>
          <input type="number" step="0.25" id="fuel-price" class="modal-input" placeholder="مثال: 15.25" value="${Store.settings.fuelPrice}" oninput="updateFuelModalPreview()">
        </div>

        <div class="modal-form-group">
          <label>عدد الكيلومترات اللي مشيتها من آخر تفويلة (كم):</label>
          <input type="number" step="1" id="fuel-km" class="modal-input" placeholder="مثال: 410 كم" value="410" oninput="updateFuelModalPreview()">
        </div>

        <!-- Real-time calculation preview box -->
        <div id="fuel-calc-preview" class="calc-preview-card"></div>

        <div class="modal-form-group">
          <label>ملاحظات (اختياري):</label>
          <input type="text" id="fuel-desc" class="modal-input" placeholder="مثال: بنزين 92 محطة شيل التجمع" value="بنزين 92">
        </div>

        <div style="display: flex; gap: 8px; margin-top: 6px;">
          <button class="btn-primary" style="flex: 2;" onclick="submitFuelModal()">
            💾 حفظ وتحديث معدل الاستهلاك
          </button>
          <button class="btn-secondary" style="flex: 1;" onclick="closeFuelModal()">
            إلغاء
          </button>
        </div>
      </div>
    </div>
  `;
  document.body.insertAdjacentHTML('beforeend', modalHtml);
  updateFuelModalPreview();
};

window.closeFuelModal = function() {
  const el = document.getElementById('fuel-modal');
  if (el) el.remove();
};

window.updateFuelModalPreview = function() {
  const liters = parseFloat(document.getElementById('fuel-liters')?.value) || 0;
  const price = parseFloat(document.getElementById('fuel-price')?.value) || Store.settings.fuelPrice;
  const km = parseFloat(document.getElementById('fuel-km')?.value) || 0;

  const totalCost = liters * price;
  const consumption = km > 0 ? (liters / km) * 100 : 0;
  const costPerKm = km > 0 ? totalCost / km : 0;

  const previewEl = document.getElementById('fuel-calc-preview');
  if (!previewEl) return;

  previewEl.innerHTML = `
    <div class="calc-preview-row">
      <span>إجمالي تكلفة التفويلة:</span>
      <strong style="color: var(--warning-amber); font-size: 14px;">${totalCost.toFixed(0)} ${Store.settings.currency}</strong>
    </div>
    <div class="calc-preview-row">
      <span>معدل الاستهلاك الفعلي:</span>
      <strong style="color: var(--accent-cyan); font-size: 14px;">${consumption.toFixed(1)} لتر / 100 كم</strong>
    </div>
    <div class="calc-preview-row" style="padding-top: 4px; border-top: 1px dashed rgba(255,255,255,0.15);">
      <span style="color: var(--neon-green); font-weight: bold;">سعر لتر/كم (التكلفة الفعلية لكل كم):</span>
      <strong style="color: var(--neon-green); font-size: 16px;">${costPerKm.toFixed(2)} ${Store.settings.currency}/كم</strong>
    </div>
  `;
};

window.submitFuelModal = function() {
  const liters = parseFloat(document.getElementById('fuel-liters')?.value) || 0;
  const price = parseFloat(document.getElementById('fuel-price')?.value) || Store.settings.fuelPrice;
  const km = parseFloat(document.getElementById('fuel-km')?.value) || 0;
  const desc = document.getElementById('fuel-desc')?.value || 'تفويلة بنزين';

  if (liters <= 0 || km <= 0) {
    alert("يرجى إدخال عدد اللترات والكيلومترات بشكل صحيح!");
    return;
  }

  const totalCost = liters * price;
  const consumption = (liters / km) * 100;
  const costPerKm = totalCost / km;

  Store.shift.expenses.unshift({
    id: Date.now(),
    category: "FUEL",
    liters: liters,
    pricePerLiter: price,
    kmSinceLast: km,
    consumption: Math.round(consumption * 10) / 10,
    costPerKm: Math.round(costPerKm * 100) / 100,
    amount: totalCost,
    desc: desc,
    time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  });

  // Automatically adapt driver vehicle consumption and price into settings!
  Store.settings.fuelConsumption = Math.round(consumption * 10) / 10;
  Store.settings.fuelPrice = price;

  persistState();
  closeFuelModal();
  AudioFx.playSuccess();
  render();
};

// 2. ENGINE OIL CHANGE MODAL LOGIC
window.openOilModal = function() {
  const existing = document.getElementById('oil-modal');
  if (existing) existing.remove();

  const modalHtml = `
    <div id="oil-modal" class="modal-backdrop" onclick="if(event.target===this) closeOilModal()">
      <div class="modal-dialog">
        <div class="modal-header">
          <h3><span>🛢️</span> تسجيل غيار زيت الموتور</h3>
          <button class="modal-close-btn" onclick="closeOilModal()">✕</button>
        </div>

        <div class="modal-form-group">
          <label>سعر لتر الزيت (${Store.settings.currency}):</label>
          <input type="number" step="10" id="oil-price" class="modal-input" placeholder="مثال: 320" value="320" oninput="updateOilModalPreview()">
        </div>

        <div class="modal-form-group">
          <label>كم ملل اتحط (حجم الزيت في الماتور):</label>
          <input type="number" step="100" id="oil-volume" class="modal-input" placeholder="مثال: 4000 ملل أو 3500 ملل" value="4000" oninput="updateOilModalPreview()">
        </div>

        <div class="modal-form-group">
          <label>الزيت دا نزل بعد كام كيلو متر (كم):</label>
          <input type="number" step="500" id="oil-km" class="modal-input" placeholder="مثال: 5000 أو 10000 كم" value="5000" oninput="updateOilModalPreview()">
        </div>

        <!-- Real-time calculation preview box -->
        <div id="oil-calc-preview" class="calc-preview-card" style="border-color: var(--accent-cyan);"></div>

        <div class="modal-form-group">
          <label>نوع الزيت وملاحظات:</label>
          <input type="text" id="oil-desc" class="modal-input" placeholder="مثال: موبيل وان 5W-30 وفلتر أصلي" value="شيل الترا 5W-40 وفلتر أصلي">
        </div>

        <div style="display: flex; gap: 8px; margin-top: 6px;">
          <button class="btn-primary" style="flex: 2; background: var(--accent-cyan); color: #0F172A;" onclick="submitOilModal()">
            💾 حفظ غيار الزيت
          </button>
          <button class="btn-secondary" style="flex: 1;" onclick="closeOilModal()">
            إلغاء
          </button>
        </div>
      </div>
    </div>
  `;
  document.body.insertAdjacentHTML('beforeend', modalHtml);
  updateOilModalPreview();
};

window.closeOilModal = function() {
  const el = document.getElementById('oil-modal');
  if (el) el.remove();
};

window.updateOilModalPreview = function() {
  const price = parseFloat(document.getElementById('oil-price')?.value) || 0;
  const volumeMl = parseFloat(document.getElementById('oil-volume')?.value) || 0;
  const km = parseFloat(document.getElementById('oil-km')?.value) || 0;

  const liters = volumeMl / 1000.0;
  const totalCost = liters * price;
  const costPerKm = km > 0 ? totalCost / km : 0;

  const previewEl = document.getElementById('oil-calc-preview');
  if (!previewEl) return;

  previewEl.innerHTML = `
    <div class="calc-preview-row">
      <span>إجمالي تكلفة غيار الزيت (${liters.toFixed(1)} لتر):</span>
      <strong style="color: var(--accent-cyan); font-size: 14px;">${totalCost.toFixed(0)} ${Store.settings.currency}</strong>
    </div>
    <div class="calc-preview-row" style="padding-top: 4px; border-top: 1px dashed rgba(255,255,255,0.15);">
      <span style="color: var(--neon-green); font-weight: bold;">تكلفة الزيت لكل كيلومتر:</span>
      <strong style="color: var(--neon-green); font-size: 16px;">${costPerKm.toFixed(2)} ${Store.settings.currency}/كم</strong>
    </div>
  `;
};

window.submitOilModal = function() {
  const price = parseFloat(document.getElementById('oil-price')?.value) || 0;
  const volumeMl = parseInt(document.getElementById('oil-volume')?.value) || 0;
  const km = parseFloat(document.getElementById('oil-km')?.value) || 0;
  const desc = document.getElementById('oil-desc')?.value || 'غيار زيت موتور';

  if (price <= 0 || volumeMl <= 0 || km <= 0) {
    alert("يرجى ملء جميع بيانات غيار الزيت بشكل صحيح!");
    return;
  }

  const liters = volumeMl / 1000.0;
  const totalCost = liters * price;
  const costPerKm = totalCost / km;

  Store.shift.expenses.unshift({
    id: Date.now(),
    category: "OIL_CHANGE",
    oilPricePerLiter: price,
    oilVolumeMl: volumeMl,
    oilLifespanKm: km,
    costPerKm: Math.round(costPerKm * 100) / 100,
    amount: totalCost,
    desc: desc,
    time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  });

  persistState();
  closeOilModal();
  AudioFx.playSuccess();
  render();
};

window.setFuelPreset = function(price) {
  Store.settings.fuelPrice = price;
  persistState();
  render();
};

window.updateSetting = function(key, val) {
  Store.settings[key] = val;
  persistState();
  render();
};

window.switchTab = function(tabName) {
  Store.currentTab = tabName;
  document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
  document.querySelectorAll('.view-tab').forEach(el => el.style.display = 'none');

  const navEl = document.getElementById(`nav-${tabName}`);
  if (navEl) navEl.classList.add('active');

  const viewEl = document.getElementById(`${tabName}-view`);
  if (viewEl) viewEl.style.display = 'flex';
};

window.switchMode = function(mode) {
  Store.activeMode = mode;
  document.querySelectorAll('.mode-btn').forEach(btn => btn.classList.remove('active'));
  if (mode === 'app') {
    document.getElementById('btn-mode-app').classList.add('active');
    document.getElementById('app-main-view').style.display = 'flex';
    document.getElementById('indrive-sim-view').style.display = 'none';
  } else {
    document.getElementById('btn-mode-sim').classList.add('active');
    document.getElementById('app-main-view').style.display = 'none';
    document.getElementById('indrive-sim-view').style.display = 'flex';
    render();
  }
};

window.triggerRandomOrder = function(type) {
  if (type === 'loss') {
    // Unprofitable order: long pickup, low fare
    Store.inputs.pickupKm = 5.0;
    Store.inputs.tripKm = 14.0;
    Store.inputs.fare = 45.0; // Guaranteed loss
    AudioFx.playWarning();
  } else if (type === 'optimal') {
    // High profit order
    Store.inputs.pickupKm = 1.0;
    Store.inputs.tripKm = 10.0;
    Store.inputs.fare = 135.0;
    AudioFx.playSuccess();
  } else {
    // Standard fair order
    Store.inputs.pickupKm = 2.2;
    Store.inputs.tripKm = 8.5;
    Store.inputs.fare = 80.0;
  }
  Store.selectedBidFare = null;
  render();
};

// Initial run
window.addEventListener('DOMContentLoaded', () => {
  render();
  switchTab('calc');
});
