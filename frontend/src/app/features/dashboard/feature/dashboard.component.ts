import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import Decimal from 'decimal.js';
import { AuthService } from '../../../core/auth/auth.service';
import { AnalyticsStore } from '../data-access/analytics.store';
import { OverviewStore } from '../data-access/overview.store';
import { ProfileStore } from '../data-access/profile.store';
import { BalanceCardComponent, BalanceCardView } from '../ui/balance-card.component';
import { CategoryBreakdownComponent } from '../ui/category-breakdown.component';
import { GoalProgressComponent } from '../ui/goal-progress.component';
import { LedgerListComponent } from '../ui/ledger-list.component';
import { PaceMeterComponent } from '../ui/pace-meter.component';
import { StatTileComponent } from '../ui/stat-tile.component';
import { BudgetRowComponent } from '../../../shared/ui/budget-row.component';
import { CashFlowChartComponent } from '../../../shared/ui/cash-flow-chart.component';
import { PageStateComponent } from '../../../shared/ui/page-state.component';
import { SkeletonRowsComponent } from '../../../shared/ui/skeleton-rows.component';
import { TrendChartComponent } from '../../../shared/ui/trend-chart.component';
import { BudgetStatus, BudgetView } from '../../../shared/util/budget-view.model';
import { activeLocale } from '../../../shared/util/locale';
import { formatMoney } from '../../../shared/util/money';
import { daysLeftInMonth, monthLabel } from '../../../shared/util/month';
import {
  cashFlowMonths,
  categoryRows,
  goalViews,
  paceView,
  compare,
  recentLines,
  savedPerMonth,
  signedPercent,
  upcomingLines,
} from './dashboard-views';

interface TileView {
  readonly value: string;
  readonly caption: string | null;
  readonly delta: string | null;
  readonly deltaTone: 'good' | 'bad' | 'neutral';
  readonly deltaUp: boolean;
}

interface AttentionItem {
  readonly key: string;
  readonly icon: string;
  readonly title: string;
  readonly detail: string;
}

const EMPTY_TILE: TileView = {
  value: '—',
  caption: null,
  delta: null,
  deltaTone: 'neutral',
  deltaUp: true,
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatDividerModule,
    MatIconModule,
    BalanceCardComponent,
    BudgetRowComponent,
    CashFlowChartComponent,
    CategoryBreakdownComponent,
    GoalProgressComponent,
    LedgerListComponent,
    PageStateComponent,
    PaceMeterComponent,
    SkeletonRowsComponent,
    StatTileComponent,
    TrendChartComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly user = inject(AuthService).currentUser;

  protected readonly profile = inject(ProfileStore);
  protected readonly overview = inject(OverviewStore);
  protected readonly analytics = inject(AnalyticsStore);

  protected readonly monthTitle = computed(() => monthLabel(this.overview.month(), activeLocale()));
  protected readonly daysLeft = computed(() => daysLeftInMonth(this.overview.month()));

  protected readonly currencies = computed(() => [
    ...new Set([
      ...this.overview.balancesByCurrency().map((balance) => balance.currency),
      ...this.analytics.currencies(),
    ]),
  ]);

  protected readonly currency = computed<string | null>(() => {
    const all = this.currencies();
    const chosen = this.analytics.currency();
    if (chosen && all.includes(chosen)) {
      return chosen;
    }
    const preferred = this.profile.profile()?.defaultCurrency;
    if (preferred && all.includes(preferred)) {
      return preferred;
    }
    return all[0] ?? preferred ?? null;
  });

  protected readonly displayName = computed(
    () => this.profile.profile()?.displayName ?? this.user()?.name ?? this.user()?.email ?? '',
  );

  protected readonly balanceCard = computed<BalanceCardView>(() => {
    const balances = this.overview.balancesByCurrency();
    const shown = balances.find((balance) => balance.currency === this.currency()) ?? balances[0];
    return {
      total: shown ? formatMoney(shown) : '—',
      others: balances
        .filter((balance) => balance !== shown)
        .map((balance) => formatMoney(balance)),
      accounts: this.overview.liveAccounts().length,
      holder: this.displayName(),
      currency: shown?.currency ?? null,
    };
  });

  private readonly months = computed(() =>
    (this.analytics.insights()?.monthlySpend ?? []).filter(
      (month) => month.expense.currency === this.currency(),
    ),
  );
  private readonly thisMonth = computed(() => this.months().at(-1) ?? null);
  private readonly lastMonth = computed(() => this.months().at(-2) ?? null);
  private readonly report = computed(
    () =>
      (this.analytics.report()?.currencies ?? []).find(
        (entry) => entry.expense.currency === this.currency(),
      ) ?? null,
  );
  private readonly forecast = computed(
    () =>
      (this.analytics.insights()?.forecast ?? []).find(
        (entry) => entry.spentSoFar.currency === this.currency(),
      ) ?? null,
  );

  protected readonly cashFlow = computed(() => cashFlowMonths(this.months()));
  protected readonly savedTrend = computed(() => savedPerMonth(this.months()));
  protected readonly hasActivity = computed(() =>
    this.months().some(
      (month) =>
        !new Decimal(month.income.amount).isZero() || !new Decimal(month.expense.amount).isZero(),
    ),
  );

  protected readonly incomeTile = computed<TileView>(() => {
    const current = this.thisMonth();
    if (!current) {
      return EMPTY_TILE;
    }
    const previous = this.lastMonth();
    return {
      ...EMPTY_TILE,
      value: formatMoney(current.income),
      caption: previous
        ? this.transloco.translate('dashboard.tiles.lastMonth', {
            amount: formatMoney(previous.income),
          })
        : null,
    };
  });

  protected readonly spendingTile = computed<TileView>(() => {
    const current = this.thisMonth();
    if (!current) {
      return EMPTY_TILE;
    }
    const forecast = this.forecast();
    const previous = this.lastMonth();
    const change =
      forecast && previous
        ? compare(
            new Decimal(forecast.projectedMonthEnd.amount),
            new Decimal(previous.expense.amount),
            activeLocale(),
          )
        : null;
    return {
      value: formatMoney(current.expense),
      caption: forecast
        ? this.transloco.translate('dashboard.tiles.onPace', {
            amount: formatMoney(forecast.projectedMonthEnd),
          })
        : null,
      delta: !change
        ? null
        : change.times
          ? this.transloco.translate('dashboard.tiles.timesLastMonth', { times: change.times })
          : this.transloco.translate('dashboard.tiles.vsLastMonth', {
              delta: signedPercent(change.percent),
            }),
      deltaTone: !change || change.percent === 0 ? 'neutral' : change.percent > 0 ? 'bad' : 'good',
      deltaUp: (change?.percent ?? 0) >= 0,
    };
  });

  protected readonly savingsTile = computed<TileView>(() => {
    const report = this.report();
    if (!report) {
      return EMPTY_TILE;
    }
    const rate = report.savingsRatePercent;
    return {
      ...EMPTY_TILE,
      value: rate === undefined || rate === null ? '—' : `${Math.round(rate)}%`,
      caption:
        rate === undefined || rate === null
          ? this.transloco.translate('dashboard.tiles.noIncome')
          : this.transloco.translate('dashboard.tiles.savedSoFar', {
              amount: formatMoney(report.net),
            }),
    };
  });

  protected readonly categories = computed(() => {
    const report = this.report();
    return report
      ? categoryRows(report, this.overview.categoriesById(), {
          other: this.transloco.translate('dashboard.categories.other'),
          uncategorised: this.transloco.translate('common.uncategorised'),
        })
      : [];
  });

  protected readonly pace = computed(() => {
    const forecast = this.forecast();
    return forecast ? paceView(forecast, this.daysLeft(), activeLocale()) : null;
  });

  protected readonly goals = computed(() => goalViews(this.analytics.goals(), 3));

  protected readonly topBudgets = computed<readonly BudgetView[]>(() => {
    const categories = this.overview.categoriesById();
    return [...this.overview.budgets()]
      .sort((a, b) => b.percent - a.percent)
      .slice(0, 4)
      .map((budget) => {
        const category = categories.get(budget.categoryId);
        return {
          id: budget.id,
          categoryName: category?.path ?? this.transloco.translate('common.unknownCategory'),
          categoryIcon: category?.icon ?? 'label',
          limit: budget.limit,
          carriedOver: budget.carriedOver,
          available: budget.available,
          rollover: budget.rollover,
          spent: budget.spent,
          remaining: budget.remaining,
          percent: budget.percent,
          status: budget.status as BudgetStatus,
          note: this.transloco.translate('budgets.leftOnly', {
            remaining: formatMoney(budget.remaining),
          }),
          foreignCurrencyEntries: budget.foreignCurrencyEntries,
        };
      });
  });

  protected readonly attention = computed<readonly AttentionItem[]>(() => {
    const categories = this.overview.categoriesById();
    const budgets = this.overview
      .budgets()
      .filter((budget) => budget.status === 'OVER' || budget.status === 'NEAR_LIMIT')
      .map((budget) => {
        const category =
          categories.get(budget.categoryId)?.name ??
          this.transloco.translate('common.unknownCategory');
        return {
          key: `budget-${budget.id}`,
          icon: budget.status === 'OVER' ? 'error' : 'warning',
          title: this.transloco.translate(
            budget.status === 'OVER' ? 'dashboard.overBudget' : 'dashboard.nearLimit',
            { category },
          ),
          detail: this.transloco.translate('dashboard.attentionDetail', {
            spent: formatMoney(budget.spent),
            limit: formatMoney(budget.limit),
            percent: budget.percent,
          }),
        };
      });
    const anomalies = (this.analytics.insights()?.anomalies ?? [])
      .filter((anomaly) => anomaly.spent.currency === this.currency())
      .map((anomaly) => ({
        key: `anomaly-${anomaly.categoryId}-${anomaly.month}`,
        icon: 'trending_up',
        title: this.transloco.translate('dashboard.attention.anomaly', {
          category: anomaly.categoryName ?? this.transloco.translate('common.uncategorised'),
        }),
        detail: this.transloco.translate('dashboard.attention.anomalyDetail', {
          spent: formatMoney(anomaly.spent),
          typical: formatMoney(anomaly.typical),
        }),
      }));
    return [...budgets, ...anomalies];
  });

  protected readonly recent = computed(() => {
    const accounts = this.overview.accountsById();
    return recentLines(
      this.overview.recentItems().slice(0, 6),
      this.overview.categoriesById(),
      (id) => accounts.get(id)?.name,
    );
  });

  protected readonly upcoming = computed(() =>
    upcomingLines(this.overview.upcomingItems().slice(0, 5), activeLocale()),
  );

  protected readonly upcomingTotal = computed(() => {
    const totals = this.overview.upcomingTotals();
    const total = totals.find((entry) => entry.currency === this.currency()) ?? totals[0];
    return total ? formatMoney(total.outgoing) : null;
  });

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.profile.load();
    void this.overview.load();
    void this.analytics.load();
  }

  protected selectCurrency(currency: string): void {
    this.analytics.select(currency);
  }

  protected open(path: string): void {
    void this.router.navigate([path]);
  }
}
