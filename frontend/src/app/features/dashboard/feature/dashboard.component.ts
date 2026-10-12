import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { OverviewStore } from '../data-access/overview.store';
import { ProfileStore } from '../data-access/profile.store';
import { BudgetRowComponent } from '../../../shared/ui/budget-row.component';
import { TransactionRowComponent } from '../../../shared/ui/transaction-row.component';
import { MoneyAmountComponent } from '../../../shared/ui/money-amount.component';
import { PageStateComponent } from '../../../shared/ui/page-state.component';
import { SkeletonRowsComponent } from '../../../shared/ui/skeleton-rows.component';
import { BudgetStatus, BudgetView } from '../../../shared/util/budget-view.model';
import { formatMoney } from '../../../shared/util/money';
import { daysLeftInMonth, monthLabel } from '../../../shared/util/month';
import {
  EntryType,
  TransactionView,
  dayLabel,
  directionOf,
} from '../../../shared/util/transaction-view.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatIconModule,
    MatListModule,
    MatProgressBarModule,
    BudgetRowComponent,
    TransactionRowComponent,
    MoneyAmountComponent,
    PageStateComponent,
    SkeletonRowsComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styles: `
    .page {
      display: grid;
      /* minmax(0, …) keeps a wide child (a filter row, a long label) from stretching the
         column past the viewport instead of wrapping inside it. */
      grid-template-columns: minmax(0, 1fr);
      gap: 1rem;
      max-inline-size: 72rem;
      margin-inline: auto;
      padding: 1rem;
    }

    .page__header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
    }

    /* The trailing actions wrap onto their own line before anything can overflow. */
    .page__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }

    .page__title {
      margin: 0;
      font: var(--mat-sys-headline-small);
    }

    .page__subtitle {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }

    .kpis {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr));
      gap: 1rem;
    }

    .kpi {
      display: grid;
      gap: 0.25rem;
    }

    .kpi__label {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: var(--mat-sys-on-surface-variant);
    }

    .kpi__value {
      font: var(--mat-sys-headline-medium);
      font-variant-numeric: tabular-nums;
    }

    .kpi__hint {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .columns {
      display: grid;
      gap: 1rem;
      align-items: start;
    }

    .card-link {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }

    @media (min-width: 1100px) {
      .columns {
        grid-template-columns: 1fr 1fr;
      }

      .page {
        padding: 1.5rem;
      }
    }
  `,
})
export class DashboardComponent {
  private readonly transloco = inject(TranslocoService);

  protected readonly profile = inject(ProfileStore);
  protected readonly overview = inject(OverviewStore);

  protected readonly monthTitle = computed(() => monthLabel(this.overview.month()));
  protected readonly daysLeft = computed(() => daysLeftInMonth(this.overview.month()));

  protected readonly balances = computed(() =>
    this.overview.balancesByCurrency().map((total) => formatMoney(total)),
  );

  protected readonly budgetTotal = computed(() => this.overview.budgetTotals()[0] ?? null);

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

  protected readonly recent = computed<readonly TransactionView[]>(() => {
    const categories = this.overview.categoriesById();
    const accounts = this.overview.accountsById();
    return this.overview
      .recentItems()
      .slice(0, 6)
      .map((item) => {
        const category = item.categoryId ? categories.get(item.categoryId) : undefined;
        return {
          id: item.id,
          description: item.description,
          meta: [dayLabel(item.bookedOn), accounts.get(item.accountId)?.name]
            .filter(Boolean)
            .join(' · '),
          icon: item.type === 'TRANSFER' ? 'swap_horiz' : (category?.icon ?? 'label'),
          amount: item.amount,
          direction: directionOf(item.type as EntryType),
          type: item.type as EntryType,
          split: item.split,
          reversed: item.reversedByTransactionId != null,
          isReversal: item.reversesTransactionId != null,
        };
      });
  });

  protected readonly upcoming = computed(() =>
    this.overview
      .upcomingItems()
      .slice(0, 5)
      .map((item) => ({
        key: `${item.ruleId}-${item.dueOn}`,
        name: item.name,
        dueOn: item.dueOn,
        amount: item.amount,
        direction: directionOf(item.type as EntryType),
      })),
  );

  protected readonly attention = computed(() =>
    this.overview
      .budgets()
      .filter((budget) => budget.status === 'OVER' || budget.status === 'NEAR_LIMIT')
      .map((budget) => {
        const category = this.overview.categoriesById().get(budget.categoryId);
        const categoryName = category?.name ?? this.transloco.translate('common.unknownCategory');
        return {
          id: budget.id,
          title: this.transloco.translate(
            budget.status === 'OVER' ? 'dashboard.overBudget' : 'dashboard.nearLimit',
            { category: categoryName },
          ),
          detail: this.transloco.translate('dashboard.attentionDetail', {
            spent: formatMoney(budget.spent),
            limit: formatMoney(budget.limit),
            percent: budget.percent,
          }),
          over: budget.status === 'OVER',
        };
      }),
  );

  constructor() {
    this.profile.load();
    void this.overview.load();
  }

  protected moneyLabel(money: { amount: string; currency: string }): string {
    return formatMoney(money);
  }
}
