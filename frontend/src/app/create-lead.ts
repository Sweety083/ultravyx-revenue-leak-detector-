import { ChangeDetectorRef, Component, DestroyRef, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService, CreateLeadRequest, errorMessage, Lead, LEAD_STATUSES, LeadStatus, RowError } from './api';
import { DISPLAY_CURRENCY } from './display-config';

function localDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
const trimmedRequired: ValidatorFn = control => control.value?.trim() ? null : { required: true };
const dateTime: ValidatorFn = control => {
  if (!control.value) return null;
  const date = new Date(control.value);
  return Number.isFinite(date.getTime()) && localDateTime(date) === control.value ? null : { dateTime: true };
};
const amount: ValidatorFn = control => {
  const value = String(control.value).trim();
  if (!value) return null;
  if (!/^\d+(\.\d{1,2})?$/.test(value)) return { amount: true };
  return Number.isSafeInteger(Math.round(Number(value) * 100)) ? null : { amountTooLarge: true };
};

@Component({
  selector: 'app-create-lead', standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule],
  templateUrl: './create-lead.html', styleUrl: './create-lead.css',
})
export class CreateLeadComponent {
  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);
  private dialog = inject(MatDialogRef<CreateLeadComponent, Lead>);
  private fb = inject(FormBuilder);
  readonly statuses = LEAD_STATUSES;
  readonly currency = DISPLAY_CURRENCY;
  readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  readonly fields = [
    { key: 'externalLeadId', label: 'Lead ID', type: 'text', required: true, max: 120 },
    { key: 'name', label: 'Full name', type: 'text', required: true, max: 200 },
    { key: 'createdAt', label: 'Created at', type: 'datetime-local', required: true, max: null },
    { key: 'contactedAt', label: 'First contacted at', type: 'datetime-local', required: false, max: null },
    { key: 'assignedTo', label: 'Owner', type: 'text', required: false, max: 150 },
    { key: 'source', label: 'Source', type: 'text', required: false, max: 150 },
    { key: 'campaign', label: 'Campaign', type: 'text', required: false, max: 150 },
    { key: 'appointmentAt', label: 'Appointment at', type: 'datetime-local', required: false, max: null },
    { key: 'attendedAt', label: 'Attended at', type: 'datetime-local', required: false, max: null },
  ] as const;
  readonly form = this.fb.nonNullable.group({
    externalLeadId: ['', [trimmedRequired, Validators.maxLength(120)]],
    name: ['', [trimmedRequired, Validators.maxLength(200)]],
    status: ['NEW' as LeadStatus, Validators.required],
    createdAt: [localDateTime(new Date()), [Validators.required, dateTime]],
    contactedAt: ['', dateTime],
    assignedTo: ['', Validators.maxLength(150)],
    source: ['', Validators.maxLength(150)],
    campaign: ['', Validators.maxLength(150)],
    appointmentAt: ['', dateTime],
    attendedAt: ['', dateTime],
    revenue: ['', amount],
  }, { validators: (control: AbstractControl) => {
    const { createdAt, contactedAt } = control.value;
    return createdAt && contactedAt && new Date(contactedAt) < new Date(createdAt) ? { contactBeforeCreation: true } : null;
  } });
  saving = false;
  error = '';
  serverErrors: Partial<Record<keyof CreateLeadRequest, string>> = {};

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.error = '';
      this.serverErrors = {};
    });
  }

  fieldError(key: keyof CreateLeadRequest): string {
    if (this.serverErrors[key]) return this.serverErrors[key]!;
    const control = this.form.controls[key];
    if (!control.touched) return '';
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('maxlength')) return `Use at most ${control.errors!['maxlength'].requiredLength} characters.`;
    if (control.hasError('dateTime')) return 'Enter a valid local date and time.';
    if (control.hasError('amount')) return 'Enter a non-negative amount with up to two decimals.';
    if (control.hasError('amountTooLarge')) return 'Amount is too large to enter accurately. Use a CSV import for this amount.';
    if (key === 'contactedAt' && this.form.hasError('contactBeforeCreation')) return 'First contact cannot be before lead creation.';
    return '';
  }

  save(): void {
    if (this.saving) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    const optionalText = (text: string) => text.trim() || null;
    const optionalDate = (text: string) => text ? new Date(text).toISOString() : null;
    const request: CreateLeadRequest = {
      externalLeadId: value.externalLeadId.trim(), name: value.name.trim(), status: value.status,
      createdAt: new Date(value.createdAt).toISOString(), contactedAt: optionalDate(value.contactedAt),
      assignedTo: optionalText(value.assignedTo), source: optionalText(value.source), campaign: optionalText(value.campaign),
      appointmentAt: optionalDate(value.appointmentAt), attendedAt: optionalDate(value.attendedAt),
      revenue: value.revenue.trim() ? Number(value.revenue) : null,
    };
    this.saving = true;
    this.error = '';
    this.serverErrors = {};
    this.dialog.disableClose = true;
    this.api.createLead(request).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: lead => this.dialog.close(lead),
      error: error => {
        this.saving = false;
        this.dialog.disableClose = false;
        this.error = errorMessage(error);
        for (const detail of (error?.error?.details || []) as RowError[]) {
          if (Object.hasOwn(this.form.controls, detail.field)) this.serverErrors[detail.field as keyof CreateLeadRequest] = detail.message;
        }
        if (error?.status === 409) this.serverErrors.externalLeadId = 'This lead ID already exists. Choose a unique ID, or update the existing record with a CSV import.';
        this.cdr.markForCheck();
      },
    });
  }
}
